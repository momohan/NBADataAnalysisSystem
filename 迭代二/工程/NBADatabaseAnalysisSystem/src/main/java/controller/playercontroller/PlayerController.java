package controller.playercontroller;

import controller.controller.DefaultController;

public class PlayerController extends DefaultController {
	
	public PlayerController() {
		this.addHandler(new GetPlayerBasicInfoRequest(), new GetPlayerBasicInfoHandler());
	}
	
}
