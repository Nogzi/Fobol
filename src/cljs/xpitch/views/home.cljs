(ns xpitch.views.home
  (:require [re-frame.core :as rf]
            [xpitch.db :as db]
            [xpitch.events :as events]
            [xpitch.subs :as subs]
            [xpitch.util :as util]
            [xpitch.views.components :as c]))

;; Column DOM nodes, used to work out the drop position while dragging.
(defonce ^:private column-els (atom {}))

(defn- standings-table [rows caption]
  [:table.table.table--standard
   [:caption.sr-only caption]
   [:thead
    [:tr
     [:th.w-pos.t-center {:scope "col"} "#"]
     [:th.w-crest {:scope "col"} [:span.sr-only "Crest"]]
     [:th.t-left {:scope "col"} "Club"]
     [:th.w-n {:scope "col"} [:abbr {:title "Played"} "P"]]
     [:th.w-n.hide-sm {:scope "col"} [:abbr {:title "Won"} "W"]]
     [:th.w-n.hide-sm {:scope "col"} [:abbr {:title "Drawn"} "D"]]
     [:th.w-n.hide-sm {:scope "col"} [:abbr {:title "Lost"} "L"]]
     [:th.w-gd {:scope "col"} [:abbr {:title "Goal difference"} "GD"]]
     [:th.w-gd {:scope "col"} [:abbr {:title "Points"} "Pts"]]
     [:th.w-form.t-left.hide-sm {:scope "col"} "Form"]]]
   [:tbody
    (when (empty? rows)
      [:tr [:td.table__empty {:col-span 10} "No clubs match your search"]])
    (for [{:keys [code name pos zone p w d l gd pts form]} rows]
      ^{:key code}
      [:tr {:class (str "zone-" (cljs.core/name zone))}
       [:td.t-center.cell-pos pos]
       [:td [c/crest code]]
       [:th.cell-team {:scope "row"} name]
       [:td p]
       [:td.hide-sm w]
       [:td.hide-sm d]
       [:td.hide-sm l]
       [:td (util/signed-int gd)]
       [:td.cell-strong pts]
       [:td.cell-form.hide-sm [c/form-pills form]]])]])

(defn- handle-key
  "Arrow keys move a league: up/down within its column, left/right across columns."
  [columns id e]
  (let [col (if (some #{id} (columns 0)) 0 1)
        index (.indexOf (columns col) id)
        to (case (.-key e)
             "ArrowUp" (when (pos? index) {:col col :index (dec index)})
             "ArrowDown" (when (< index (dec (count (columns col)))) {:col col :index (+ index 2)})
             "ArrowLeft" (when (= col 1) {:col 0 :index (min index (count (columns 0)))})
             "ArrowRight" (when (= col 0) {:col 1 :index (min index (count (columns 1)))})
             nil)]
    (when to
      (.preventDefault e)
      (rf/dispatch [::events/move-league id to true]))))

(defn- league-card [id columns]
  (let [{:keys [name country badge matchday team-count]} @(rf/subscribe [::subs/league id])
        rows @(rf/subscribe [::subs/filtered-standings id])
        collapsed? (contains? @(rf/subscribe [:collapsed]) id)
        {:keys [armed] dragging :id} @(rf/subscribe [:drag])
        body-id (str "league-" (cljs.core/name id) "-table")
        title-id (str "league-" (cljs.core/name id) "-title")]
    [:section {:class ["card" "league-card" (when (= dragging id) "league-card--dragging")]
               :data-league (cljs.core/name id)
               :aria-labelledby title-id
               :draggable (= armed id)
               :on-drag-start (fn [e]
                                (set! (.. e -dataTransfer -effectAllowed) "move")
                                (.setData (.-dataTransfer e) "text/plain" (cljs.core/name id))
                                (rf/dispatch [::events/drag-start id]))
               :on-drag-end #(rf/dispatch [::events/drag-end])}
     [:header {:class ["league-card__header" (when-not collapsed? "league-card__header--open")]}
      [:button.drag-handle {:type "button"
                            :aria-label (str "Reorder " name ". Use arrow keys to move.")
                            :on-pointer-down #(rf/dispatch-sync [::events/arm id])
                            :on-key-down #(handle-key columns id %)}
       [c/icon :grip]]
      [c/league-badge id badge]
      [:div.league-card__title
       [:h2 {:id title-id} name]
       [:p country " · Matchday " matchday " · " team-count " clubs"]]
      [:a.link {:href (str "#/league/" (cljs.core/name id))} "View league " [c/icon :arrow-right 14]]
      [:button.collapse-toggle {:type "button"
                                :aria-expanded (not collapsed?)
                                :aria-controls body-id
                                :aria-label (str (if collapsed? "Expand " "Collapse ") name)
                                :on-click #(rf/dispatch [::events/toggle-collapsed id])}
       [c/icon (if collapsed? :chevron-down :chevron-up)]]]
     [:div {:id body-id :hidden collapsed?}
      [standings-table rows (str name " standings")]]]))

(defn- drop-indicator [id]
  (let [{:keys [name]} @(rf/subscribe [::subs/league id])]
    [:div.drop-indicator {:aria-hidden true}
     [:span.drop-indicator__dot]
     [:span.drop-indicator__line]
     [:span "Drop " name " here"]]))

(defn- on-drag-over [col e]
  (.preventDefault e)
  (set! (.. e -dataTransfer -dropEffect) "move")
  (let [cards (array-seq (.querySelectorAll (@column-els col) "[data-league]"))
        y (.-clientY e)
        index (or (first (keep-indexed (fn [i el]
                                         (let [r (.getBoundingClientRect el)]
                                           (when (< y (+ (.-top r) (/ (.-height r) 2))) i)))
                                       cards))
                  (count cards))]
    ;; The handler ignores this when no league is being dragged or nothing changed.
    (rf/dispatch [::events/drag-over col index])))

(defn- league-column [col ids columns]
  (let [{dragging :id drop :drop} @(rf/subscribe [:drag])
        indicator-at? #(and dragging drop (= (:col drop) col) (= (:index drop) %))]
    [:div.league-column {:ref #(if % (swap! column-els assoc col %) (swap! column-els dissoc col))
                         :on-drag-over #(on-drag-over col %)
                         :on-drop (fn [e] (.preventDefault e) (rf/dispatch [::events/drop]))}
     (for [[index id] (map-indexed vector ids)]
       ^{:key id}
       [:<>
        (when (indicator-at? index) [drop-indicator dragging])
        [league-card id columns]])
     (when (indicator-at? (count ids)) [drop-indicator dragging])]))

(defn home-page []
  (let [leagues @(rf/subscribe [::subs/leagues])
        error (:leagues @(rf/subscribe [:errors]))
        columns @(rf/subscribe [:columns])
        all-collapsed? (= (count @(rf/subscribe [:collapsed])) (count db/league-ids))
        mode @(rf/subscribe [:data-mode])
        updated (some-> @(rf/subscribe [:updated-at]) util/format-updated)
        through (some-> @(rf/subscribe [:data-through]) util/format-day)
        provider @(rf/subscribe [:provider])
        notes @(rf/subscribe [::subs/notes])]
    [:main.page
     [:div.page-header
      [:div.page-header__title
       [:p.overline "Season 2026/27"]
       [:h1.display "League tables"]
       [:p.muted-body
        (case mode
          :live (str "Standings across 6 leagues"
                     (when provider (str " · Data: " provider))
                     (when through (str " · Results through " through))
                     (when updated (str " · Checked " updated)))
          :sample "Sample data · live data isn't available right now"
          "Current standings across 6 leagues")]]
      [:div.page-header__actions
       [:button.button {:type "button" :on-click #(rf/dispatch [::events/set-all-collapsed (not all-collapsed?)])}
        (if all-collapsed? "Expand all" "Collapse all")]
       [:button.button {:type "button" :on-click #(rf/dispatch [::events/reset-order])} "Reset order"]]]
     (when (seq notes)
       [c/data-notes notes])
     [:div.toolbar
      [c/zone-legend]
      [:p.hint [c/icon :grip 14] " Drag a league to reorder · Your order is saved"]]
     (cond
       error [c/status-message [:<> "Couldn't load leagues (" error "). "
                                [:button.link {:type "button" :on-click #(rf/dispatch [::events/fetch-leagues])} "Retry"]]]
       (nil? leagues) [c/status-message "Loading leagues…"]
       :else [:div.league-grid
              (for [[col ids] (map-indexed vector columns)]
                ^{:key col} [league-column col ids columns])])
     [:p.sr-only {:aria-live "polite"} @(rf/subscribe [:announcement])]]))
