(ns planwise.client.projects.api
  (:require [ajax.core :refer [GET POST]]
            [planwise.client.api :refer [json-request]]))

(defn load-project [id & handlers]
  (GET
    (str "/api/projects/" id)
    (json-request {:id id} handlers)))

(defn load-projects [& handlers]
  (GET
    "/api/projects/"
    (json-request {} handlers)))

(defn create-project [params & handlers]
  (POST
    "/api/projects/"
    (json-request params handlers)))
