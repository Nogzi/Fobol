(ns xpitch.app
  (:require [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [xpitch.db :as db]
            [xpitch.events :as events]
            [xpitch.subs]
            [xpitch.views.components :as c]
            [xpitch.views.home :as home]
            [xpitch.views.league :as league]))

(defn root []
  (let [route @(rf/subscribe [:route])]
    [:<>
     [c/top-nav]
     (if (= :league (:name route))
       ^{:key (:id route)} [league/league-page (:id route)]
       [home/home-page])]))

(defonce ^:private react-root (delay (rdc/create-root (.getElementById js/document "root"))))

(defn- render! [] (rdc/render @react-root [root]))

(defn ^:dev/after-load reload!
  "Called by shadow-cljs after hot code reload."
  []
  (rf/clear-subscription-cache!)
  (render!))

(defn init []
  (rf/dispatch-sync [::events/initialize (db/parse-hash (.-hash js/location))])
  (.addEventListener js/window "hashchange"
                     #(rf/dispatch [::events/route-changed (db/parse-hash (.-hash js/location))]))
  ;; Releases the drag "arm" if the pointer goes up without a drag starting.
  (.addEventListener js/window "pointerup" #(rf/dispatch [::events/arm nil]))
  (render!))
