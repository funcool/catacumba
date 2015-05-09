(ns catacumba.handlers.interceptor
  "Helpers for intercept execution of handlers chain.
  Primarily for traceability and recording metrics."
  (:require [catacumba.impl.routing :as routing]
            [catacumba.impl.handlers :as handlers]
            [catacumba.impl.context :as context])
  (:import ratpack.registry.Registries
           ratpack.handling.Chain
           ratpack.handling.Context
           ratpack.handling.Handler
           ratpack.exec.Execution
           ratpack.func.Block
           ratpack.exec.ExecInterceptor
           ratpack.exec.ExecInterceptor$ExecType))

(defn- exec-interceptor
  [interceptor]
  (reify ExecInterceptor
    (^void intercept [_ ^Execution exc ^ExecInterceptor$ExecType t ^Block b]
      (let [continuation #(.execute b)
            exectype (if (= t ExecInterceptor$ExecType/BLOCKING)
                       :blocking
                       :compute)]
        (interceptor exc exectype continuation)))))

(defn interceptor
  "Start interceptor from current context.

  It wraps the rest of route chain the execution. It receive a
  continuation (as a cloure function) that must be called in
  order for processing to proceed."
  [context interceptor]
  (let [^Context ctx (:catacumba/context context)]
    (.addInterceptor ctx
                     (exec-interceptor interceptor)
                     (reify Block
                       (^void execute [_]
                         (context/delegate context))))))

(defmethod routing/attach-route :interceptor
  [^Chain chain [_ interceptor']]
  (let [handler #(interceptor % interceptor')]
    (.handler chain ^Handler (handlers/adapter handler))))
