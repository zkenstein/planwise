(ns planwise.client.projects.handlers
  (:require [re-frame.core :refer [register-handler path dispatch]]
            [accountant.core :as accountant]
            [planwise.client.routes :as routes]
            [planwise.client.projects.api :as api]
            [clojure.string :refer [split capitalize join]]
            [planwise.client.projects.db :as db]
            [re-frame.utils :as c]))

(def in-projects (path [:projects]))
(def in-current-project (path [:projects :current]))
(def in-filter-definitions (path [:filter-definitions]))

;; ---------------------------------------------------------------------------
;; Facility types handlers

(register-handler
 :projects/fetch-facility-types
 (fn [db [_]]
   (api/fetch-facility-types :projects/facility-types-received)
   db))

(register-handler
 :projects/facility-types-received
 in-filter-definitions
 (fn [db [_ types]]
   (assoc db :facility-type types)))

;; ---------------------------------------------------------------------------
;; Project listing

(register-handler
 :projects/load-projects
 in-projects
 (fn [db [_ project-id]]
   (api/load-projects :projects/projects-loaded)
   (assoc db :view-state :loading)))

(register-handler
 :projects/projects-loaded
 in-projects
 (fn [db [_ projects]]
   (let [region-ids (->> projects
                         (map :region-id)
                         (remove nil?)
                         (set))]
     (dispatch [:regions/load-regions-with-preview region-ids])
     (assoc db
            :view-state :list
            :list projects))))

;; Project searching

(register-handler
 :projects/search
 in-projects
 (fn [db [_ value]]
   (assoc db :search-string value)))

;; ---------------------------------------------------------------------------
;; Project creation

(register-handler
 :projects/begin-new-project
 in-projects
 (fn [db [_]]
   (assoc db :view-state :create-dialog)))

(register-handler
 :projects/cancel-new-project
 in-projects
 (fn [db [_]]
   (assoc db :view-state :view)))

(register-handler
 :projects/create-project
 in-projects
 (fn [db [_ project-data]]
   (api/create-project project-data :projects/project-created)
   (assoc db :view-state :creating)))

(register-handler
 :projects/project-created
 in-projects
 (fn [db [_ project-data]]
   (let [project-id (:id project-data)]
     (when (nil? project-id)
       (throw "Invalid project data"))
     (accountant/navigate! (routes/project-facilities {:id project-id}))
     (assoc db
            :view-state :view
            :list (cons project-data (:list db))
            :current (db/new-viewmodel project-data)))))

;; ---------------------------------------------------------------------------
;; Loading a project

(defn section->with
  [section]
  (case section
    :demographics nil
    :facilities :facilities
    :transport :isochrones))

(register-handler
 :projects/navigate-project
 in-projects
 (fn [db [_ project-id section]]
   (if (not= project-id (get-in db [:current :project-data :id]))
     (dispatch [:projects/load-project project-id (section->with section)])
     (case section
       :facilities
       (dispatch [:projects/load-facilities])
       :transport
       (dispatch [:projects/load-isochrones])
       nil))
   db))

(register-handler
 :projects/load-project
 in-projects
 (fn [db [_ project-id with-data]]
   (api/load-project project-id with-data
                     :projects/project-loaded :projects/not-found)
   (assoc db :view-state :loading
             :current db/empty-viewmodel)))

(register-handler
 :projects/not-found
 in-projects
 (fn [db [_]]
   (accountant/navigate! (routes/home))
   db))

(register-handler
 :projects/project-loaded
 in-projects
  (fn [db [_ project-data]]
    (dispatch [:regions/load-regions-with-geo [(:region-id project-data)]])
    (assoc db :view-state :view
              :current (db/new-viewmodel project-data))))

(register-handler
 :projects/delete-project
 in-projects
 (fn [db [_ id]]
   (api/delete-project id)
   (accountant/navigate! (routes/home))
   (update db :list (partial filterv #(not= (:id %) id)))))

(defn- facilities-criteria [current-project-db]
  (let [filters (get-in current-project-db [:facilities :filters])
        project-region-id (get-in current-project-db [:project-data :region-id])]
    (assoc filters :region project-region-id)))

(register-handler
 :projects/load-facilities
 in-current-project
 (fn [db [_ force?]]
   (when (or force? (nil? (get-in db [:facilities :list])))
     (api/fetch-facilities (facilities-criteria db) :projects/facilities-loaded))
   db))

(register-handler
 :projects/facilities-loaded
 in-current-project
 (fn [db [_ response]]
   (-> db
       (assoc-in [:facilities :count] (:count response))
       (assoc-in [:facilities :list] (:facilities response)))))

; REFACTOR: The simplify constant is duplicated in fn db/project-filters
(register-handler
 :projects/load-isochrones
 in-current-project
 (fn [db [_ force?]]
   (if-let [time (get-in db [:transport :time])]
     (when (or force? (nil? (get-in db [:facilities :isochrones time 0.4])))
       (api/fetch-facilities-with-isochrones (facilities-criteria db) {:threshold time, :simplify 0.4} :projects/isochrones-loaded)))
   db))

; REFACTOR: Most of this logic is duplicated in db/update-viewmodel
(register-handler
 :projects/isochrones-loaded
 in-current-project
 (fn [db [_ {:keys [map-key unsatisfied-count facilities threshold simplify], :as response}]]
   (let [isochrones  (->> facilities
                       (filter #(some? (:isochrone %)))
                       (map (juxt :id :isochrone))
                       (flatten)
                       (apply hash-map))]
     (-> db
       (assoc :demand-map-key map-key)
       (assoc :unsatisfied-count unsatisfied-count)
       (update-in [:facilities :isochrones threshold simplify] #(merge % isochrones))))))

;; ---------------------------------------------------------------------------
;; Project filter updating

(register-handler
 :projects/toggle-filter
 in-current-project
 (fn [db [_ filter-group filter-key filter-value]]
   (let [path [filter-group :filters filter-key]
         current-filter (get-in db path)
         toggled-filter (if (contains? current-filter filter-value)
                          (disj current-filter filter-value)
                          (conj current-filter filter-value))
         new-db (-> db
                    (assoc-in path (set toggled-filter))
                    (assoc-in [:facilities :isochrones] nil))
         filters (db/project-filters new-db)
         project-id (get-in db [:project-data :id])]
     (api/update-project project-id filters :facilities
                         :projects/project-updated)
     new-db)))

(register-handler
 :projects/set-transport-time
 in-current-project
  (fn [db [_ time]]
    (let [new-db (assoc-in db [:transport :time] time)
          filters (db/project-filters new-db)
          project-id (get-in db [:project-data :id])]
      (api/update-project project-id filters :isochrones
                          :projects/project-updated)
      new-db)))

(register-handler
 :projects/project-updated
 in-current-project
 (fn [db [_ project]]
   (db/update-viewmodel db project)))

;; ---------------------------------------------------------------------------
;; Project map view handlers

(register-handler
 :projects/update-position
 in-current-project
 (fn [db [_ new-position]]
   (assoc-in db [:map-view :position] new-position)))

(register-handler
 :projects/update-zoom
 in-current-project
 (fn [db [_ new-zoom]]
   (assoc-in db [:map-view :zoom] new-zoom)))
