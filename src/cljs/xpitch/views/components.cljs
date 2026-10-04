(ns xpitch.views.components
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [xpitch.db :as db]
            [xpitch.events :as events]))

(def ^:private icon-paths
  {:grip [:<> [:circle {:cx 9 :cy 5 :r 1}] [:circle {:cx 9 :cy 12 :r 1}] [:circle {:cx 9 :cy 19 :r 1}]
          [:circle {:cx 15 :cy 5 :r 1}] [:circle {:cx 15 :cy 12 :r 1}] [:circle {:cx 15 :cy 19 :r 1}]]
   :chevron-down [:path {:d "m6 9 6 6 6-6"}]
   :chevron-up [:path {:d "m18 15-6-6-6 6"}]
   :chevron-right [:path {:d "m9 18 6-6-6-6"}]
   :arrow-right [:<> [:path {:d "M5 12h14"}] [:path {:d "m12 5 7 7-7 7"}]]
   :arrow-down [:<> [:path {:d "M12 5v14"}] [:path {:d "m19 12-7 7-7-7"}]]
   :arrow-up [:<> [:path {:d "m5 12 7-7 7 7"}] [:path {:d "M12 19V5"}]]
   :arrow-up-down [:<> [:path {:d "m21 16-4 4-4-4"}] [:path {:d "M17 20V4"}] [:path {:d "m3 8 4-4 4 4"}] [:path {:d "M7 4v16"}]]
   :search [:<> [:circle {:cx 11 :cy 11 :r 8}] [:path {:d "m21 21-4.3-4.3"}]]
   :moon [:path {:d "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"}]
   :sun [:<> [:circle {:cx 12 :cy 12 :r 4}] [:path {:d "M12 2v2"}] [:path {:d "M12 20v2"}]
         [:path {:d "m4.93 4.93 1.41 1.41"}] [:path {:d "m17.66 17.66 1.41 1.41"}] [:path {:d "M2 12h2"}]
         [:path {:d "M20 12h2"}] [:path {:d "m6.34 17.66-1.41 1.41"}] [:path {:d "m19.07 4.93-1.41 1.41"}]]
   :chart [:<> [:path {:d "M3 3v18h18"}] [:path {:d "M18 17V9"}] [:path {:d "M13 17V5"}] [:path {:d "M8 17v-3"}]]
   :calendar [:<> [:rect {:width 18 :height 18 :x 3 :y 4 :rx 2}] [:path {:d "M16 2v4"}] [:path {:d "M8 2v4"}] [:path {:d "M3 10h18"}]]})

(defn icon
  ([name] (icon name 16))
  ([name size]
   [:svg {:width size :height size :view-box "0 0 24 24" :fill "none" :stroke "currentColor"
          :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round" :aria-hidden true}
    (icon-paths name)]))

(defn crest [code]
  [:span.crest {:aria-hidden true} code])

(defn league-badge
  ([id code] (league-badge id code :md))
  ([id code size]
   [:span {:class ["league-badge" (str "league-badge--" (name size))]
           :style {:background (str "var(--league-" (name id) ")")}
           :aria-hidden true}
    code]))

(def ^:private result-label {"W" "Win" "D" "Draw" "L" "Loss"})

(defn form-pills [form]
  [:span.form {:aria-label (str "Last five: " (str/join ", " (map result-label form)))}
   (for [[i r] (map-indexed vector form)]
     ^{:key i} [:span {:class ["form-pill" (str "form-pill--" r)] :aria-hidden true} r])])

(defn progress-bar
  "Proportional bar; ratio is clamped to 0–1."
  ([ratio] (progress-bar ratio nil))
  ([ratio class]
   [:span {:class ["bar" class] :aria-hidden true}
    [:span.bar__fill {:style {:width (str (* 100 (max 0 (min 1 ratio))) "%")}}]]))

(defn segmented-control [{:keys [options value on-change label]}]
  [:div.segmented {:role "radiogroup" :aria-label label}
   (for [{v :value l :label} options]
     ^{:key v}
     [:button {:type "button" :role "radio" :aria-checked (= v value)
               :class ["segment" (when (= v value) "segment--active")]
               :on-click #(on-change v)}
      l])])

(def ^:private zones
  [[:ucl "Champions League"] [:uel "Europa League"] [:uecl "Conference League"] [:relegation "Relegation"]])

(defn zone-legend []
  [:ul.legend
   (for [[zone label] zones]
     ^{:key zone}
     [:li [:span.legend__swatch {:style {:background (str "var(--zone-" (name zone) ")")}}] label])])

(def ^:private league-names
  {:premier-league "Premier League" :la-liga "La Liga" :bundesliga "Bundesliga"
   :serie-a "Serie A" :ligue-1 "Ligue 1" :superliga "3F Superliga"})

(defn top-nav []
  (let [route @(rf/subscribe [:route])
        query @(rf/subscribe [:query])
        theme @(rf/subscribe [:theme])
        active (if (= :league (:name route)) (:id route) :all)
        tabs (into [[:all "All leagues" "#/"]]
                   (map (fn [id] [id (league-names id) (str "#/league/" (name id))]))
                   db/league-ids)]
    [:header.nav
     [:a.nav__logo {:href "#/" :aria-label "xPitch home"}
      [:span.nav__mark [icon :chart 18]]
      [:span.nav__wordmark "xPitch"]]
     [:nav.nav__tabs {:aria-label "Leagues"}
      (for [[id label href] tabs]
        ^{:key id}
        [:a {:href href :class ["tab" (when (= id active) "tab--active")]
             :aria-current (when (= id active) "page")}
         label])]
     [:label.nav__search
      [icon :search]
      [:input {:type "search" :placeholder "Search teams…" :aria-label "Search teams"
               :value query
               :on-change #(rf/dispatch-sync [::events/set-query (.. % -target -value)])}]]
     [:button.icon-button {:type "button"
                           :aria-label (if (= theme "dark") "Switch to light theme" "Switch to dark theme")
                           :on-click #(rf/dispatch [::events/toggle-theme])}
      [icon (if (= theme "dark") :sun :moon)]]]))

(defn status-message [text]
  [:div.status-message {:role "status"} text])

(defn data-notes
  "Explains fallbacks to sample data or partial advanced stats."
  [notes]
  [:div.data-notes {:role "note"}
   (for [n notes] ^{:key n} [:p n])])
