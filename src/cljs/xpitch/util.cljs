(ns xpitch.util
  (:require [clojure.string :as str]))

(def ^:private diacritics (js/RegExp. "\\p{Diacritic}" "gu"))

(defn- normalize [s]
  (-> (.normalize s "NFD")
      (.replace diacritics "")
      (str/replace "ø" "o")
      (str/replace "æ" "ae")
      str/lower-case))

(defn matches-query?
  "Case- and accent-insensitive substring match (\"koln\" finds \"Köln\")."
  [text query]
  (let [q (str/trim (or query ""))]
    (or (str/blank? q) (str/includes? (normalize text) (normalize q)))))

(def dash "–")

(defn fixed [n digits] (if (number? n) (.toFixed n digits) dash))

(defn signed-int [n] (cond (not (number? n)) dash (pos? n) (str "+" n) :else (str n)))

(defn signed-fixed [n digits]
  (if (number? n)
    (str (cond (pos? n) "+" (neg? n) "−" :else "") (fixed (abs n) digits))
    dash))

(defn format-day
  "\"2026-09-20T19:45\" → \"20 Sep 2026\"."
  [iso]
  (when-let [[_ y m d] (re-find #"^(\d{4})-(\d{2})-(\d{2})" (or iso ""))]
    (.toLocaleDateString (js/Date. (js/Date.UTC (js/parseInt y) (dec (js/parseInt m)) (js/parseInt d)))
                         "en-GB" #js {:day "numeric" :month "short" :year "numeric" :timeZone "UTC"})))

(defn format-updated
  "ISO timestamp → e.g. \"Sun 4 Oct 2026, 18:30\" in the viewer's locale/time zone."
  [iso]
  (let [d (js/Date. iso)]
    (when-not (js/isNaN (.getTime d))
      (str (.toLocaleDateString d "en-GB" #js {:weekday "short" :day "numeric" :month "short" :year "numeric"})
           ", "
           (.toLocaleTimeString d "en-GB" #js {:hour "2-digit" :minute "2-digit"})))))
