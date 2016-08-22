(ns planwise.client.datasets.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :refer [capitalize]]
            [re-com.core :as rc]
            [planwise.client.config :as config]
            [planwise.client.components.common :as common]
            [planwise.client.datasets.components.listing :refer [no-datasets-view datasets-list]]
            [planwise.client.datasets.components.new-dataset :refer [new-dataset-dialog]]
            [planwise.client.datasets.db :as db]
            [planwise.client.utils :as utils]))


(defn open-auth-popup []
  (.open js/window
         "/oauth2/start"
         "PlanwiseAuth"
         "chrome=yes,centerscreen=yes,width=600,height=400"))

(defn import-warning []
  [:p.warning
   [:b "Current facilities will be replaced"]
   " by the imported sites. This is a time consuming process and "
   [:b "can take several hours to complete"]
   ", depending on the number of imported facilities."])

(defn selected-collection-options
  []
  (let [selected (subscribe [:datasets/selected])
        state (subscribe [:datasets/state])]
    (fn []
      (let [valid? (:valid? @selected)
            fields (:fields @selected)
            type-field (:type-field @selected)
            importing? (db/importing? @state)
            can-import? (and (not importing?) valid? (some? type-field))]
        [:div.import-settings
         (if (nil? fields)
           [common/loading-placeholder]
           (if valid?
             [:div
              [:div
               [:label "Use field as facility type "]
               [rc/single-dropdown
                :choices fields
                :disabled? importing?
                :label-fn :name
                :on-change #(dispatch [:datasets/select-type-field %])
                :model type-field]]
              [import-warning]
              [:div.actions
               [:button.primary
                {:on-click #(dispatch [:datasets/start-import!])
                 :disabled (not can-import?)}
                (if importing? "Importing..." "Import collection")]]]
             [:p
              "Collection cannot be imported into facilities."]))]))))

(defn collection-item
  [{:keys [id name description selected? count on-click]}]
  [:li {:class (when selected? "selected")
        :on-click on-click}
   [:h1 name]
   [:p.description description]
   [:p.count (str count " sites")]
   (when selected?
     [selected-collection-options])])

(defn collections-list
  [collections]
  (let [selected (subscribe [:datasets/selected])
        state (subscribe [:datasets/state])]
    (fn [collections]
      (let [selected-coll (:collection @selected)
            importing? (db/importing? @state)]
        [:ul.collections
         (for [coll collections]
           (let [coll-id (:id coll)
                 selected? (and (not importing?) (= coll selected-coll))]
             [collection-item (assoc coll
                                     :selected? selected?
                                     :key coll-id
                                     :on-click (when-not importing?
                                                 #(dispatch [:datasets/select-collection coll])))]))]))))

(defn facilities-summary []
  (let [facility-count (subscribe [:datasets/facility-count])
        state (subscribe [:datasets/state])
        server-status (subscribe [:datasets/server-status])]
    (fn []
      (let [importing? (db/importing? @state)
            cancelling? (db/cancelling? @state)]
        [:div.dataset-header
         [:h2 "Facilities"]
         (if-not importing?
           (let [last-result (db/last-import-result @server-status)]
             [:div
              [:p
               "There are "
               [:b (utils/pluralize @facility-count "facility" "facilities")]
               " in the system."]
              (when (some? last-result)
                [:div.bottom-right
                 [:p "Last import: " last-result]])])
           (let [step (if (= :import-requested @state)
                        "Starting"
                        (db/server-status->string @server-status))]
             [:div
              [:h3
               "Import in progress: "
               [:b step]]
              [:div.bottom-right
               [:button.danger
                {:type :button
                 :on-click #(dispatch [:datasets/cancel-import!])
                 :disabled cancelling?}
                (if cancelling? "Cancelling..." "Cancel")]]]))]))))

(defn resmap-collections []
  (let [resourcemap (subscribe [:datasets/resourcemap])
        state (subscribe [:datasets/state])]
    (fn []
      (let [importing? (db/importing? @state)]
        [:div {:class (when importing? "disabled")}
         [:h3 "Available Resourcemap collections "
          (when-not importing?
            [common/refresh-button {:on-click #(dispatch [:datasets/reload-info])}])]
         [collections-list (:collections @resourcemap)]]))))

(defn resmap-authorise []
  [:div
   [:h3 "PlanWise needs authorisation to access your Resourcemap collections."]
   [:button.primary
    {:on-click #(open-auth-popup)}
    "Authorise"]])

(defn datasets-view []
  (let [resourcemap (subscribe [:datasets/resourcemap])]
    (fn []
      [:div
       [facilities-summary]
       [:div.resmap
        [:p "You can import facilities from a "
         [:a {:href config/resourcemap-url :target "resmap"} "Resourcemap"]
         " collection. The collection to import needs to have the required fields "
         "(facility type, etc.) to be usable. Also only sites with a location will be imported."]
        (if (:authorised? @resourcemap)
          [resmap-collections]
          [resmap-authorise])]])))


;; ----------------------------------------------------------------------------
;; Datasets list

(defn datasets-page
  []
  (let [view-state (subscribe [:datasets/view-state])
        datasets (subscribe [:datasets/list])
        filtered-datasets (subscribe [:datasets/filtered-list])]
    (fn []
      (dispatch [:datasets/load-datasets])
      [:article.datasets
       (cond
         (nil? @datasets) [common/loading-placeholder]
         (empty? @datasets) [no-datasets-view]
         :else [datasets-list @filtered-datasets])
       (when (db/show-dialog? @view-state)
         [common/modal-dialog {:on-backdrop-click
                               #(dispatch [:datasets/cancel-new-dataset])}
          [new-dataset-dialog]])])))
