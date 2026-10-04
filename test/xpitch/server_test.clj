(ns xpitch.server-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [xpitch.server :as server]))

(defn- get-edn [uri]
  (let [{:keys [status body]} (server/app {:request-method :get :uri uri
                                           :headers {"accept" "application/edn"}})]
    [status (edn/read-string (slurp body))]))

(deftest leagues-endpoint
  (let [[status {:keys [mode leagues]}] (get-edn "/api/leagues")]
    (is (= 200 status))
    (is (= :sample mode) "no API key in tests, so sample data")
    (is (= [:premier-league :la-liga :bundesliga :serie-a :ligue-1 :superliga] (map :id leagues)))
    (is (= 12 (count (:standings (last leagues)))))))

(deftest league-endpoint
  (let [[status body] (get-edn "/api/leagues/serie-a")]
    (is (= 200 status))
    (is (= "Serie A" (:name body)))
    (is (= :sample (:source body)))
    (is (= 4 (count (:overview body))))
    (is (= #{:overall :home :away} (set (keys (:advanced body)))))))

(deftest status-endpoint
  (let [[status body] (get-edn "/api/status")]
    (is (= 200 status))
    (is (contains? body :live-enabled))))

(deftest unknown-league
  (is (= 404 (first (get-edn "/api/leagues/mls")))))
