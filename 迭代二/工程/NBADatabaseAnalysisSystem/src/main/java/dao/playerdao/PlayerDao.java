package dao.playerdao;

import java.util.ArrayList;
import java.util.Map;

import entity.PlayerEntity;

public interface PlayerDao {

	public ArrayList<Map<PlayerEntity, String>> getPlayerInfo(PlayerInfoType type);
	public void close();
	
}
