(ns infolog.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :name
 (fn [db]
   (reaction (:name @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :problems
 (fn [db]
   (reaction (:infolog-problems @db))))

(re-frame/register-sub
 :location
 (fn [db]
   (reaction (:directory @db))))

