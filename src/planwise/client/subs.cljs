(ns planwise.client.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]
            [planwise.client.projects.subs]
            [planwise.client.current-project.subs]
            [planwise.client.datasets.subs]
            [planwise.client.regions.subs]))


;; Subscriptions
;; -------------------------------------------------------

(register-sub
 :current-page
 (fn [db _]
   (reaction (:current-page @db))))

(register-sub
 :page-params
 (fn [db _]
   (reaction (:page-params @db))))

