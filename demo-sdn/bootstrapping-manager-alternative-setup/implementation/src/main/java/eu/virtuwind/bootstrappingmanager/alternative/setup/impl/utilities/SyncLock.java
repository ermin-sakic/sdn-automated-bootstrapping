/**
  *
  * @filename SyncLock.java
  *
  * @date 15.04.18
  *
  * @author Mirza Avdic
  *
  *
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *  Data structure used to store conditional variables for each thread pair
 *  InitialFlowWriter and ConfigureNewOpenFlowNodeNBI necessary for proper
 *  synchronization
 */
public class SyncLock {
    public static Map<String, SyncLock> threadSync = Collections.synchronizedMap(new HashMap<>());
    private boolean condVariable = false;

    public boolean isCondVariable() {
        return condVariable;
    }

    public void setCondVariable(boolean condVariable) {
        this.condVariable = condVariable;
    }
}
