(ns xpitch.test-runner
  (:require [clojure.test :as t]
            [xpitch.stats-test]
            [xpitch.live-test]
            [xpitch.football-data-test]
            [xpitch.server-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-all-tests #"xpitch\..*-test")]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
