(ns om-manager.core
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [om-manager.core :refer [go-while-mounted]])
  (:require
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer [html] :include-macros true]
   [cljs.core.async :as async :refer [>! <! put! chan mult]]))

(enable-console-print!)

(defn om-manager
  [data owner {:keys [managed-comp] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      (let [manager-chan (chan)]
        {:manager-chan    manager-chan
         :manager/unmount (mult manager-chan)}))

    om/IWillUnmount
    (will-unmount [_]
      (put! (om/get-state owner :manager-chan) true))

    om/IRenderState
    (render-state [_ state]
      (om/build managed-comp data {:state (dissoc state :manager-chan) :opts opts}))))

(defn child
  [data owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [chan1 chan2] :as state}]
      (html
       [:div
        [:button {:onClick #(put! chan1 true)} "Increment chan 1!"]
        [:button {:onClick #(put! chan2 true)} "Increment chan 2!"]]))))

(defn my-component
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:inc1 0, :inc2 0})

    om/IWillMount
    (will-mount [_]
      (let [{:keys [chan1 chan2]} (om/get-state owner)]
        (go-while-mounted owner [msg chan1]
          (println "--------------------------------------------------------------------------------")
          (println "Got msg from chan 1! " msg)
          (println "go block for chan 1 state: " (select-keys (om/get-state owner) [:inc1 :inc2]))
          (om/update-state! owner :inc1 inc))

        ;; For comparison
        (go (while true
              (let [msg (<! chan2)]
                (println "--------------------------------------------------------------------------------")
                (println "Got msg from chan 2! " msg)
                (println "go block for chan 2 state: " (select-keys (om/get-state owner) [:inc1 :inc2]))
                (om/update-state! owner :inc2 inc))))))

    om/IRenderState
    (render-state [_ {:keys [inc1 inc2 chan1 chan2] :as state}]
      (html
       [:div
        [:p (str "Chan 1: " inc1 " <- uses go-while-mounted")]
        [:p (str "Chan 2: " inc2 " <- uses normal go block, not cleaned on unmounting")]
        (om/build child data {:state {:chan1 chan1 :chan2 chan2}})]))))

(defn base-comp
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:chan1 (chan), :chan2 (chan)})

    om/IRenderState
    (render-state [_ {:keys [visible?] :as state}]
      (html
       [:div
        [:p "Pressing the button below will mount a \"managed\" component with one properly handled go block and one not.
             Both will work correctly on the initial mount, but after unmounting and remounting, you'll see that #2 will behave strangely
             (and progressively so as you unmount/re-mount). Make sure you watch the console to see what state is getting dumped out by each."]
        [:div [:button {:onClick #(om/set-state! owner :visible? (not visible?))} (if visible? "Unmount component!" "Mount component!")]
         (if visible?
           (om/build om-manager data {:state state :opts {:managed-comp my-component}}))]]))))

(om/root base-comp {} {:target (.getElementById js/document "root")})
