(ns xpitch.api-football
  "Minimal API-Football (v3) client. Tracks the rate-limit headers so the sync
  never spends the last few requests of the daily quota."
  (:require [clojure.string :as str]
            [jsonista.core :as json])
  (:import (java.net URI URLEncoder)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (java.nio.charset StandardCharsets)
           (java.time Duration)))

(def base-url "https://v3.football.api-sports.io")

(defonce ^:private client
  (delay (-> (HttpClient/newBuilder) (.connectTimeout (Duration/ofSeconds 10)) .build)))

(defonce limits
  ;; {:day-limit :day-remaining :minute-limit :minute-remaining}, from the last response headers.
  (atom {}))

(defn- header-long [^java.net.http.HttpHeaders headers name]
  (some-> (.firstValue headers name) (.orElse nil) parse-long))

(defn- query-string [params]
  (str/join "&" (for [[k v] params]
                  (str (name k) "=" (URLEncoder/encode (str v) StandardCharsets/UTF_8)))))

(defn- api-errors
  "API-Football reports problems (bad key, plan limits) as 200 OK with a non-empty `errors`."
  [body]
  (let [errors (get body "errors")]
    (when (seq errors)
      (if (map? errors)
        (str/join "; " (map (fn [[k v]] (str k ": " v)) errors))
        (str/join "; " (map str errors))))))

(defn get!
  "GET `path` with `params`; returns the decoded `response` array.
  Throws ex-info with :type :budget, :http or :api."
  [{:keys [api-key reserve]} path params]
  (let [{:keys [day-remaining minute-remaining]} @limits]
    (when (and day-remaining (<= day-remaining reserve))
      (throw (ex-info (str "Daily request budget reached (" day-remaining " left, keeping " reserve " in reserve)")
                      {:type :budget})))
    (when (and minute-remaining (<= minute-remaining 0))
      (Thread/sleep 61000)))
  (let [uri (URI/create (str base-url path (when (seq params) (str "?" (query-string params)))))
        request (-> (HttpRequest/newBuilder uri)
                    (.header "x-apisports-key" api-key)
                    (.timeout (Duration/ofSeconds 30))
                    .GET
                    .build)
        response (.send @client request (HttpResponse$BodyHandlers/ofString))
        headers (.headers response)]
    (swap! limits merge
           (into {} (remove (comp nil? val))
                 {:day-limit (header-long headers "x-ratelimit-requests-limit")
                  :day-remaining (header-long headers "x-ratelimit-requests-remaining")
                  :minute-limit (header-long headers "X-RateLimit-Limit")
                  :minute-remaining (header-long headers "X-RateLimit-Remaining")}))
    (when-not (= 200 (.statusCode response))
      (let [status (.statusCode response)
            body (str/trim (str (.body response)))
            detail (cond (= status 403) "check that the API key is correct and active"
                         (= status 429) "rate limited"
                         (seq body) (subs body 0 (min 200 (count body))))]
        (throw (ex-info (str "API-Football HTTP " status " for " path (when detail (str " (" detail ")")))
                        {:type :http :status status}))))
    (let [body (json/read-value (.body response))]
      (when-let [msg (api-errors body)]
        (throw (ex-info (str "API-Football: " msg) {:type :api :path path})))
      (get body "response"))))

;; ---------- Endpoints ----------

(defn standings [config league season]
  (get! config "/standings" {:league league :season season}))

(defn teams [config league season]
  (get! config "/teams" {:league league :season season}))

(def finished-statuses "FT-AET-PEN")

(defn finished-fixtures [config league season]
  (get! config "/fixtures" {:league league :season season :status finished-statuses}))

(defn fixtures-by-ids
  "Up to 20 fixtures per call; each item includes its `statistics`."
  [config ids]
  {:pre [(<= 1 (count ids) 20)]}
  (get! config "/fixtures" {:ids (str/join "-" ids)}))
