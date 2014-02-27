(ns slacker.client.state)

(defonce scheduled-clients (atom {}))

;; a map to store host:port to request-map mapping
(defonce server-requests (atom {}))
