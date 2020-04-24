(ns firedraft.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [firedraft.core-test]))

(doo-tests 'firedraft.core-test)

