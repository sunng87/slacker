(ns slacker.client.rseq
  )

(defprotocol RemoteSeqClient
  (seq-open [this fname args])
  (seq-next [this seq-id])
  (seq-close [this seq-id]))

(defn- rseq* [sc seq-id]
  (lazy-seq
   (cons (seq-next sc seq-id) (rseq* sc seq-id))))

(defn rseq [sc fname args]
  (let [seq-id (seq-open sc fname args)]
    (rseq* sc seq-id)))

