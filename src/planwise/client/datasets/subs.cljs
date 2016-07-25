(ns planwise.client.datasets.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]))

(register-sub
 :datasets/state
 (fn [db [_]]
   (reaction (get-in @db [:datasets :state]))))

(register-sub
 :datasets/facility-count
 (fn [db [_]]
   (reaction (get-in @db [:datasets :facility-count]))))

(register-sub
 :datasets/resourcemap
 (fn [db [_]]
   (reaction (get-in @db [:datasets :resourcemap]))))

(register-sub
 :datasets/selected
 (fn [db [_]]
   (reaction (get-in @db [:datasets :selected]))))

(register-sub
 :datasets/raw-status
 (fn [db [_]]
   (reaction (get-in @db [:datasets :raw-status]))))