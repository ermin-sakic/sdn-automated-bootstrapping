package eu.virtuwind.resourcemanager.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
/**
 * Created by Ali on 01/02/17.
 * Path class to store the Flows and the switches they belong to, provides all the relevant methods for retrieving
 */
public class Path {

    //list of flows to be stored
    private List<Flow> flowlist;

    //unique id
    private Long uniqueID = 0L;

    //map to store map between each flow and edge_switch
    private Map<Flow,String> flowStringMap = new HashMap<>();


    /**
     * Constructor to instantiate a Path object
     * @param id - Unique ID for thr path
     * @param flows - the list of flows to be stored
     */
    public Path(Long id, List<Flow> flows ) { uniqueID = id;  flowlist = flows; }

    /**
     *
     * @return list of flows
     */
    public List<Flow> getFlowlist() {
        return flowlist;
    }

    /**
     * Getting the uniqueID of the path
     * @return - Long - Unique path id
     */
    public Long getUniqueID() {
        return uniqueID;
    }

    /**
     * get the flow switch map
     * @return - flowstring map
     */
    public Map<Flow, String> getFlowStringMap() {
        return flowStringMap;
    }

    /**
     * Method to add to the map
     * @param flow - the key of the map
     * @param edge_switch - the value of the map
     */
    public void addToMap(Flow flow, String edge_switch ) {

        flowStringMap.put(flow,edge_switch);

    }


}
