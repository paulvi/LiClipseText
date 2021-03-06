/**
 * Copyright (c) 2005-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
/*
 * Created on 13/10/2005
 */
package org.brainwy.liclipsetext.shared_core.callbacks;

public interface ICallback<Ret, Arg> {

    Ret call(Arg arg);
}
