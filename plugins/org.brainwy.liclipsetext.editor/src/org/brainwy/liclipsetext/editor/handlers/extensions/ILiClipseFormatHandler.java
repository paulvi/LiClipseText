/**
 * Copyright (c) 2013-2016 by Brainwy Software Ltda. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package org.brainwy.liclipsetext.editor.handlers.extensions;


public interface ILiClipseFormatHandler extends ILiClipseHandler {

    String format(LiClipseActionContext liClipseActionContext) throws Exception;

}
