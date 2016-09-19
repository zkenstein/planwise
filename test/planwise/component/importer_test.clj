(ns planwise.component.importer-test
  (:require [planwise.component.importer :as importer]
            [planwise.component.datasets :as datasets]
            [planwise.boundary.resmap :as resmap]
            [planwise.component.taskmaster :as taskmaster]
            [planwise.test-system :refer [test-system with-system]]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan <! >!] :as async]))

(def resmap-type-field
  {:name "Type",
   :code "type",
   :kind "select_one",
   :config {:options [{:id 1, :code "hospital", :label "Hospital"}
                      {:id 2, :code "health center", :label "Health Center"}
                      {:id 3, :code "dispensary", :label "Dispensary"}],
            :next_id 4}})

(def fixture-data
  [[:users
    [{:id 1 :email "john@doe.com" :full_name "John Doe" :last_login nil :created_at (time/ago (time/minutes 5))}]]
   [:datasets
    [{:id 1 :name "dataset1" :description "" :owner_id 1 :collection_id 1 :import_mappings nil}
     {:id 2 :name "dataset2" :description "" :owner_id 1 :collection_id 2 :import_mappings nil}]]])

(defn mock-resmap []
  (reify resmap/Resmap
    (get-collection-sites
      [service user-ident coll-id params]
      [])

    (find-collection-field
      [service user-ident coll-id field-id]
      (assoc resmap-type-field :id field-id))))

(defn mock-taskmaster []
  (let [channel (chan)]
    (async/go-loop []
      (if-let [{response-channel :channel} (<! channel)]
        (>! response-channel [:ok nil]))
      (recur))
    channel))

(defn importer-service [system]
  (-> (importer/importer)
    (merge (select-keys system [:taskmaster :datasets :resmap]))
    (component/start)))

(defn system []
  (into
    (test-system {:fixtures {:data fixture-data}})
    {:taskmaster (mock-taskmaster)
     :resmap (mock-resmap)
     :datasets (component/using (datasets/datasets-store) [:db])}))

(deftest resume-importer-job
  (timbre/with-level :warn
    (with-system (system)

      ; Start new import for a dataset
      (let [importer (importer-service system)]
        (let [[result statūs] (importer/run-import-for-dataset importer 1 {:user-email "john@doe.com", :user-id 1})]
          (is (= :ok result))
          (is (= :importing (:status (get statūs 1)))))

          ; Proceed to next task in the import process
        (let [{task-id :task-id} (taskmaster/next-task importer)]
          (is (= [1 :import-types] task-id))
          (let [jobs (taskmaster/task-completed importer task-id :new-types)]
            (is (= :request-sites (:state (get jobs 1))))))

        (component/stop importer))

      ; Create a new importer component that should reload the jobs state from the previous one
      (let [importer (importer-service system)]

        ; Check that the next task is correctly restored
        (let [{task-id :task-id} (taskmaster/next-task importer)]
          (is (= [1 [:import-sites 1]] task-id)))

        (component/stop importer)))))


(deftest facility-type-ctor-test
  (let [type-field {:code "facility_type"
                    :options {1 10,
                              2 11}}

        site       {:id 1, :name "s1",
                    :lat -1.28721692771827, :long 36.8136030697266,
                    :properties {:facility_type 1}}

        facility-type (importer/facility-type-ctor type-field)]

    (is (= 10 (facility-type site)))))


(deftest sites->facilities-test
  (let [type-field          {:code "facility_type",
                             :options {1 10,
                                       2 11}}

        sites               [{:id 1, :name "s1",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {:facility_type 1}}

                             ;; sites without location are ignored
                             {:id 2, :name "s2",
                              :properties {:facility_type 1}}

                             ;; sites without a type are ignored
                             {:id 3, :name "s3",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {}}
                             {:id 4, :name "s4",
                              :lat -1.28721692771827, :long 36.8136030697266,
                              :properties {:facility_type nil}}]

        facility-type       (importer/facility-type-ctor type-field)
        facilities          (importer/sites->facilities sites facility-type)

        expected-facilities [{:lat -1.28721692771827,
                              :lon 36.8136030697266,
                              :name "s1",
                              :site-id 1,
                              :type-id 10}]]

    (is (= expected-facilities facilities))))
