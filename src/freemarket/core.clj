(ns freemarket.core
  (:use [freemarket.impl])
  (:require [clojure.tools.logging :as log])
  (:import  [java.util.concurrent ExecutionException]
            [freemarket.impl WorkQueue]))


(defrecord Producer     [workers work-queue])
(defrecord Consumer     [workers result-queue])

(defn work-queue->seq
  ;; TODO docs
  [queue]
  {:pre  [(instance? WorkQueue queue)]
   :post [(seq? %)]}
  (iterator-fn->lazy-seq
    (fn []
      (let [next-item (.take queue)]
        ;; TODO: docs/cleanup
        (if (instance? Throwable next-item)
          (throw next-item))
        (if (= work-queue-sentinel-complete next-item)
          (do
            ;; TODO: doc
            (.put queue work-queue-sentinel-complete)
            nil)
          next-item)))))

(defn producer
  ;; TODO: docs preconds
  ([work-fn num-workers]
    (producer work-fn num-workers 0))
  ([work-fn num-workers max-work]
    {:pre  [(fn? work-fn)
            (pos? num-workers)
            (>= max-work 0)]
     :post [(instance? Producer %)
            (instance? WorkQueue (:work-queue %))
            (every? future? (:workers %))]}
    (let [queue       (work-queue max-work)
          workers     (doall (for [_ (range num-workers)]
                               (build-worker
                                 #(iterator-fn->lazy-seq work-fn)
                                 #(.put queue %))))
          supervisor  (future
                        ;; TODO: doc
                        (try
                          (doseq [worker workers] @worker)
                          (catch Throwable th
                            (let [ex (if (instance? ExecutionException th)
                                       (.getCause th)
                                       th)]
                              (log/error ex "Something horrible happened!"
                                "Shutting down producers.")
                              (mapv future-cancel workers)
                              (.put queue ex))))
                        (.put queue work-queue-sentinel-complete))]
      (Producer. workers queue))))

(defn consumer
  ;; TODO: docs preconds
  ([producer work-fn num-workers] (consumer producer work-fn num-workers 0))
  ([{producer-queue :work-queue :as producer} work-fn num-workers max-results]
    {:pre  [(instance? Producer producer)
            (instance? WorkQueue producer-queue)]
     :post [(instance? Consumer %)
            (instance? WorkQueue (:result-queue %))
            (every? future? (:workers %))]}
    (let [result-queue   (work-queue max-results)
          workers        (doall (for [_ (range num-workers)]
                                  (build-worker
                                    #(work-queue->seq producer-queue)
                                    #(.put result-queue (validate-consumer-work (work-fn %))))))
          supervisor     (future
                           ;; TODO: doc
                           (try
                             (doseq [worker workers] @worker)
                             (catch Throwable th
                               (let [ex (if (instance? ExecutionException th)
                                          (.getCause th)
                                          th)]
                                 (log/error ex "Something horrible happened!"
                                   "Shutting down consumers.")
                                 (mapv future-cancel workers)
                                 (.put result-queue ex))))
                           (.put result-queue work-queue-sentinel-complete))]
      (Consumer. workers result-queue))))
