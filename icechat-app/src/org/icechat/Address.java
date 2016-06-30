package org.icechat;

public class Address {
	private String router;
	private String simulator;
	
	public Address(String address) {
		int idx = address.indexOf('[');
		if(idx == -1) {
			router = address;
		}
		else {
			router = address.substring(0, idx);
			simulator = address.substring(idx + 1, address.lastIndexOf(']'));
		}
	}

	public String getRouter() {
		return router;
	}

	public String getSimulator() {
		return simulator;
	}
	
	
}
