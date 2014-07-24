(ns om-manager.core)

(defmacro go-while-mounted
  [owner binding & body]
  `(cljs.core.async.macros/go
     (loop []
       (let [unmount-mult#          (om.core/get-state ~owner :manager/unmount)
             unmount-chan#          (cljs.core.async/tap unmount-mult# (cljs.core.async/chan))
             [~(first binding) ch#] (cljs.core.async/alts! [~(second binding) unmount-chan#])]
         (if (= ch# ~(second binding))
           (do
             ~@body
             (recur)))))))
