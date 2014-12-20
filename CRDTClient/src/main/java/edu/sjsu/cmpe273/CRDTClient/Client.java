package edu.sjsu.cmpe273.CRDTClient;

public class Client {

    public static void main(String[] args) throws Exception {
        
    	System.out.println("Client (Start)");
        
        CRDTClient crdtc = new CRDTClient();
        boolean requestStatus = crdtc.put(1, "a");
        if (requestStatus) {
        	Thread.sleep(4000);
        	requestStatus = crdtc.put(1, "b");
        	if (requestStatus) {
        		Thread.sleep(4000);
            	String temp = crdtc.get(1);
            	System.out.println("Value obtained "+temp);
        	} else {
            	System.out.println("Write fail");
        	}
        } else {

        	System.out.println("1st write fail");
        }	
        System.out.println("Client in working state");
        
    }

}
