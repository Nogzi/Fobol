(ns xpitch.views.league
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [xpitch.events :as events]
            [xpitch.subs :as subs]
            [xpitch.util :as util]
            [xpitch.views.components :as c]))

(def ^:private columns
  [{:key :p :label "P" :title "Played" :fmt str}
   {:key :pts :label "Pts" :title "Points" :fmt str :strong? true}
   {:key :x-pts :label "xPts" :title "Expected points" :fmt #(util/fixed % 1) :strong? true}
   {:key :xg :label "xG" :title "Expected goals" :fmt #(util/fixed % 1)}
   {:key :xga :label "xGA" :title "Expected goals against" :fmt #(util/fixed % 1)}
   {:key :xgd :label "xGD" :title "Expected goal difference" :fmt #(util/signed-fixed % 1)}
   {:key :poss :label "Poss" :title "Average possession" :fmt #(if (number? %) (str (util/fixed % 1) "%") util/dash)}
   {:key :shg :label "Sh/g" :title "Shots per game" :fmt #(util/fixed % 1)}
   {:key :sotg :label "SoT/g" :title "Shots on target per game" :fmt #(util/fixed % 1)}])

(def ^:private splits
  [{:value :overall :label "Overall"} {:value :home :label "Home"} {:value :away :label "Away"}])

(defn- width-class [k] (str "w-" (str/replace (name k) "-" "")))

(defn- stat-card [{:keys [label value delta delta-positive? caption ratio]}]
  [:article.card.stat-card
   [:h3.overline.overline--muted label]
   [:p.stat-card__value-row
    [:span.stat-value value]
    (when delta
      [:span {:class ["delta" (if delta-positive? "delta--up" "delta--down")]} delta])]
   [c/progress-bar ratio]
   [:p.caption.muted caption]])

(defn- leaderboard-card [{:keys [title subtitle entries]}]
  [:article.card.leaderboard
   [:header.leaderboard__head
    [:h3 title]
    [:p.caption.muted subtitle]]
   (if (empty? entries)
     [:p.caption.muted.leaderboard__empty "Not enough data yet"]
     [:ol.leaderboard__list
    (for [[i {:keys [code team value ratio]}] (map-indexed vector entries)]
      ^{:key code}
      [:li
       [:span.leaderboard__rank (inc i)]
       [c/crest code]
       [:span.leaderboard__team team]
       [c/progress-bar ratio "bar--compact"]
       [:span.leaderboard__value value]])])])

(defn- advanced-table [rows {sort-key :key dir :dir} caption]
  [:div.table-scroll
   [:table.table.table--advanced
    [:caption.sr-only caption]
    [:thead
     [:tr
      [:th.w-pos.t-center {:scope "col"} [:abbr {:title "Current league position"} "#"]]
      [:th.w-crest {:scope "col"} [:span.sr-only "Crest"]]
      [:th.t-left.w-team {:scope "col"} "Club"]
      (for [{:keys [key label title]} columns
            :let [active? (= key sort-key)]]
        ^{:key key}
        [:th {:scope "col" :class (width-class key)
              :aria-sort (if active? (if (= dir :asc) "ascending" "descending") "none")}
         [:button {:type "button"
                   :class ["sort" (when active? "sort--active")]
                   :title (str "Sort by " (str/lower-case title))
                   :on-click #(rf/dispatch [::events/sort-by key])}
          label
          [c/icon (cond (not active?) :arrow-up-down (= dir :asc) :arrow-up :else :arrow-down) 12]]])]]
    [:tbody
     (when (empty? rows)
       [:tr [:td.table__empty {:col-span (+ 3 (count columns))} "No clubs match your search"]])
     (for [{:keys [code name pos zone xgd] :as row} rows]
       ^{:key code}
       [:tr {:class (str "zone-" (cljs.core/name zone))}
        [:td.t-center.cell-pos pos]
        [:td [c/crest code]]
        [:th.cell-team {:scope "row"} name]
        (for [{:keys [key fmt strong?]} columns]
          ^{:key key}
          [:td {:class [(when strong? "cell-strong")
                        (when (= key sort-key) "cell-sorted")
                        (when (and (= key :xgd) (number? xgd)) (if (>= xgd 0) "cell-pos-delta" "cell-neg-delta"))]}
           (fmt (get row key))])])]]])

(defn- section [title-id title subtitle aside & children]
  (into [:section.section {:aria-labelledby title-id}
         [:div.section__head
          [:div
           [:h2.h2 {:id title-id} title]
           [:p.muted-body subtitle]]
          aside]]
        children))

(defn league-page [id]
  (let [summary @(rf/subscribe [::subs/league id])
        detail @(rf/subscribe [::subs/detail id])
        error (get @(rf/subscribe [:errors]) id)
        split @(rf/subscribe [:split])
        sort-state @(rf/subscribe [:sort])
        rows @(rf/subscribe [::subs/advanced-rows id])
        {:keys [name country badge matchday total-matchdays team-count matches-played]} (or detail summary)
        sort-col (some #(when (= (:key %) (:key sort-state)) %) columns)]
    [:main.page.page--league
     [:nav.breadcrumbs {:aria-label "Breadcrumb"}
      [:a {:href "#/"} "All leagues"]
      [c/icon :chevron-right 12]
      [:span {:aria-current "page"} name]]
     (cond
       error [c/status-message [:<> "Couldn't load this league (" error "). "
                                [:button.link {:type "button" :on-click #(rf/dispatch [::events/fetch-league id])} "Retry"]]]
       (nil? detail) [c/status-message "Loading league…"]
       :else
       [:<>
        [:div.league-header
         [c/league-badge id badge :lg]
         [:div.league-header__title
          [:h1.display name]
          [:p.muted-body country " · Season 2026/27 · Matchday " matchday " of " total-matchdays " · " team-count " clubs"]]
         [:button.button.button--icon {:type "button" :title "Matchday selection is not available yet"}
          [c/icon :calendar] (str "Matchday " matchday) [c/icon :chevron-down]]]

        (when-let [note (:note detail)]
          [c/data-notes [note]])

        [section "overview-title" "League overview" (str "Season-to-date averages across " matches-played " matches") nil
         [:div.grid-4 (for [k (:overview detail)] ^{:key (:label k)} [stat-card k])]]

        [section "team-stats-title" "Team stats" "Per-team leaders · per-match averages" nil
         [:div.grid-4 (for [b (:leaderboards detail)] ^{:key (:title b)} [leaderboard-card b])]]

        [section "standings-title" "Advanced standings"
         (str "Sorted by " (str/lower-case (:title sort-col)) " (" (:label sort-col) ") "
              (if (= :desc (:dir sort-state)) "↓" "↑") " · Select any column header to sort")
         [c/segmented-control {:options splits :value split :label "Home or away split"
                               :on-change #(rf/dispatch [::events/set-split %])}]
         [:div.card [advanced-table rows sort-state (str name " advanced standings, " (cljs.core/name split))]]
         [:div.footnote
          [c/zone-legend]
          [:span.caption.muted
           (str "# = current league position · xPts = "
                (if (= :odds (:x-pts-basis detail))
                  "points expected from pre-match betting odds"
                  "points expected from chance quality (xG)")
                (when-let [p (:provider detail)] (str " · Data: " p)))]]]])]))
