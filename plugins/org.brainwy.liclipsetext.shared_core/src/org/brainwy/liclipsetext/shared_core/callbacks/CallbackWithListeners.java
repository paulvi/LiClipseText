/**
 * Copyright (c) 2005-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package org.brainwy.liclipsetext.shared_core.callbacks;

import org.brainwy.liclipsetext.shared_core.log.Log;
import org.brainwy.liclipsetext.shared_core.structure.OrderedSet;

public class CallbackWithListeners<X> implements ICallbackWithListeners<X> {

    private final OrderedSet<ICallbackListener<X>> listeners;

    public CallbackWithListeners() {
        this.listeners = new OrderedSet<ICallbackListener<X>>();
    }

    public CallbackWithListeners(int initialCapacity) {
        this.listeners = new OrderedSet<ICallbackListener<X>>(initialCapacity);
    }

    @Override
    public Object call(X obj) {
        Object result = null;
        for (ICallbackListener<X> listener : this.listeners) {
            try {
                Object callResult = listener.call(obj);
                if (callResult != null) {
                    result = callResult;
                }
            } catch (Throwable e) {
                //Should never fail!
                Log.log(e);
            }
        }
        return result;
    }

    @Override
    public void registerListener(ICallbackListener<X> listener) {
        this.listeners.add(listener);
    }

    @Override
    public void unregisterListener(ICallbackListener<X> listener) {
        this.listeners.remove(listener);
    }

    @Override
    public void unregisterAllListeners() {
        this.listeners.clear();
    }

}
