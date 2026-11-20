//Prevayler(TM) - The Free-Software Prevalence Layer.
//Copyright (C) 2001-2004 Klaus Wuestefeld
//This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//Contributions: Carlos Villela, Jacob Kjome

package org.prevayler.foundation.monitor;

import java.io.File;

/**
 * A Monitor for interesting events in the system.
 */
public interface Monitor {

    /**
     * An exception was thrown while trying to access a file.
     */
    void notify(Class clazz, String message, File file, Exception ex);

}
