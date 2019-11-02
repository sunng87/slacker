(ns slacker.interceptors.deadline
  (:require [slacker.common :refer [*timeout*]]))

;; built-in extension, using id < 0
;; (def deadline-extension-id -1)

(defn assoc-deadline-extension [extensions call-options deadline-extension-id]
  (let [timeout-ms (or (:timeout call-options) *timeout*)]
    (assoc extensions deadline-extension-id (+ timeout-ms (System/currentTimeMillis)))))

(defn abort-request-when-deadline-exceeded [deadline-extension-id req]
  (if-let [deadline (-> req :extensions (get deadline-extension-id))]
    (if (< deadline (System/currentTimeMillis))
      (assoc req :code :canceled)
      req)
    req))

(defn client-interceptor [deadline-extension-id]
  {:pre (fn [req]
          (let [call-options (:call-options req)]
            (update req :extensions assoc-deadline-extension call-options deadline-extension-id)))})

(defn server-interceptor [deadline-extension-id]
  {:before (partial abort-request-when-deadline-exceeded deadline-extension-id)})
