package dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

public class Dao implements DaoInterface {
	
	Connection connection;
	FileAlterationMonitor monitor;
	ArrayList<String[]> performance = new ArrayList<String[]>();
	//创建新数据库，运行耗时3秒
	public void newDatabase() throws Exception{
		
		//连接到数据库
		Class.forName("org.sqlite.JDBC");
    	connection = DriverManager.getConnection("jdbc:sqlite:NBADatabase.db");
    	
    	//创建表格
		Statement stat = connection.createStatement();
    	stat.executeUpdate("drop table if exists players");
    	stat.executeUpdate("drop table if exists matches");
    	stat.executeUpdate("drop table if exists overtime_matches");
    	stat.executeUpdate("drop table if exists player_match_performance");
    	stat.executeUpdate("drop table if exists teams");
    	stat.executeUpdate("drop table if exists paths");
    	
    	BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(
								new File("./src/main/java/dao/database.sql"))));
		String sql = "";
		String strTemp = "";
		while((strTemp = reader.readLine()) != null){
			sql += strTemp;
		}
		stat.executeUpdate(sql);
		reader.close();
	}

	public void readFiles(String path)throws Exception {
		//连接到数据库
		Class.forName("org.sqlite.JDBC");
    	connection = DriverManager.getConnection("jdbc:sqlite:NBADatabase.db");
    	
    	Calendar c = Calendar.getInstance();
    	System.out.println("readPlayerFiles:"+c.getTimeInMillis());
    	//从文件中读取
    	readPlayerFiles(path+"players/info/");
    	
    	readTeamFiles(path+"teams/teams");
    	
    	readMatchFiles(path+"matches/");
    	
    	storePath(path);
    	
	};
	
	private void storePath(String path)throws Exception{
		PreparedStatement prep = connection.prepareStatement("insert into paths values("
				+ "?,?)");
		prep.setString(2,path);
		prep.addBatch();
		connection.setAutoCommit(false);
		prep.executeBatch();
		connection.commit();
	}
	
	//读取制定路径下的players文件，并将结果写入数据库中，运行耗时1s
	private void readPlayerFiles(String path)throws Exception{
		File[] fileList = new File(path).listFiles();
		PreparedStatement prep = connection.prepareStatement(""
				+ "insert into players values("
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?)");
		for(File file : fileList){
			Pattern pattern = Pattern.compile("│([\\w', \\(\\)-\\.]*)");
			String[] player = findMatcher(file,pattern);
			
			//player数组的长度固定为9
			for(int i = 0 ;i < player.length ;i ++){
				prep.setString(i+2,player[i]);
			}
			prep.addBatch();
		}
		
		connection.setAutoCommit(false);
		prep.executeBatch();
		connection.setAutoCommit(true);
		
	};
		
	//返回指定文件的每一行中符合pattern中group（1）的字符串，每一行的结果放入数组中的一项
	private String[] findMatcher(File file,Pattern pattern)throws Exception{
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
		String strTemp = br.readLine();
		String resultString = "";
		
		while(strTemp != null){
			Matcher matcher = pattern.matcher(strTemp);
			if(matcher.find()){
				resultString +=matcher.group(1)+";";
			}
			strTemp = br.readLine();
		}
		br.close();
		return resultString.split(";");
	}
	
	//读取指定路径下的teams文件，并将结果写入数据库中，耗时0.1s
	private void readTeamFiles(String path) throws Exception{
		File file = new File(path);
		PreparedStatement prep = connection.prepareStatement(""
				+ "insert into teams values("
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?)");
		
		Pattern pattern = Pattern.compile("║([\\w\\t\\(\\)\\& │]*)║");
		String[] teamList = findMatcher(file,pattern);
		for(int i = 0;i < teamList.length;i++){
			String[] team = teamList[i].split("│");
			for(int j = 0;j < team.length;j ++){
				prep.setString(j+2,team[j].trim());
			}
			prep.addBatch();
		}
		connection.setAutoCommit(false);
		prep.executeBatch();
		connection.setAutoCommit(true);
	};
	
	//读取制定路径下的matches文件，将数据读入数据库中
	private void readMatchFiles(String path)throws Exception{
		File[] fileList = new File(path).listFiles();
		storeMatches(fileList);
		storeOvertimeMatches(fileList);
		storePlayerMatchPerformance(fileList);
		Calendar c = Calendar.getInstance();
		System.out.println(c.getTimeInMillis());
	};
	
	//将fileList中文件中的比赛概要信息添加到数据库的mathces表格中,耗时0.5s
	private void storeMatches(File[] fileList)throws Exception{
		PreparedStatement prep = connection.prepareStatement(""
				+ "insert into matches values ("
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?)");
		for(File file:fileList){
			storeMatches(file,prep);
		}
		connection.setAutoCommit(false);
		prep.executeBatch();
		connection.setAutoCommit(true);
	}
	
	//将match文件中的比赛概要信息添加到PreparedStatement的batch中
	private void storeMatches(File file,PreparedStatement matchesPrep)throws Exception{
		String season = file.getName().split("_")[0];
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
		String strTemp = br.readLine();
		System.out.println(strTemp);
		String[] match = strTemp.split(";");
		strTemp = br.readLine();
		String[] sectionScore = strTemp.split(";");
		int homeId = getTeamId(match[1].split("-")[0]);
		int awayId = getTeamId(match[1].split("-")[1]);
		
		matchesPrep.setString(2,season);
		matchesPrep.setString(3,match[0]);
		matchesPrep.setString(4,homeId+"");
		matchesPrep.setString(5,awayId+"");
		matchesPrep.setString(6,match[2].split("-")[0]);
		matchesPrep.setString(7,match[2].split("-")[1]);
		matchesPrep.setString(8,sectionScore[0].split("-")[0]);
		matchesPrep.setString(9,sectionScore[0].split("-")[1]);
		matchesPrep.setString(10,sectionScore[1].split("-")[0]);
		matchesPrep.setString(11,sectionScore[1].split("-")[1]);
		matchesPrep.setString(12,sectionScore[2].split("-")[0]);
		matchesPrep.setString(13,sectionScore[2].split("-")[1]);
		matchesPrep.setString(14,sectionScore[3].split("-")[0]);
		matchesPrep.setString(15,sectionScore[3].split("-")[1]);
		matchesPrep.setString(16,(sectionScore.length>4)+"");
		matchesPrep.addBatch();
		br.close();
		
	}
	
	//将match文件中的加时赛保存到数据库中,耗时0.9s
	private void storeOvertimeMatches(File[] fileList)throws Exception{
		PreparedStatement prep = connection.prepareStatement(""
				+ "insert into overtime_matches values("
				+ "?,?,?,?)");
		for(File file : fileList){
			storeOvertimeMatches(file,prep);
		}
		connection.setAutoCommit(false);
		prep.executeBatch();
		connection.setAutoCommit(true);
	} 
	
	//将match文件中的加时赛添加到PreparedStatement的batch中
	private void storeOvertimeMatches(File file,PreparedStatement prep)throws Exception{
		BufferedReader br = new BufferedReader(new InputStreamReader (new FileInputStream(file),"UTF-8"));
		String[] match = file.getName().split("_");
		int hId = getTeamId(match[2].split("-")[0]);
		br.readLine();//忽略第一行
		String[] strTemp = br.readLine().split(";");
		int matchId = getMatchId(match[0],match[1],hId);
		if(strTemp.length>4){
			for(int i = 0 ;i < strTemp.length-4;i++){
				prep.setString(1,matchId+"");
				prep.setString(2,(i+1)+"");
				prep.setString(3,strTemp[4+i].split("-")[0]);
				prep.setString(4,strTemp[4+i].split("-")[1]);
				prep.addBatch();
			}
		}
		br.close();
	}
	private void storePlayerMatchPerformance()throws Exception{
		connection.close();
		Class.forName("org.sqlite.JDBC");
    	connection = DriverManager.getConnection("jdbc:sqlite:NBADatabase.db");
		PreparedStatement prep = connection.prepareStatement(""
				+ "insert into player_match_performance values ("
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?,?,?,?,?,?,?,?,"
				+ "?,?,?)");
		for(int i = 0 ;i < performance.size();i ++){
			for(int j = 0;j < 23;j ++){
				System.out.print(performance.get(i)[j]);
				prep.setString(j+1,performance.get(i)[j]);
			}
			System.out.println();
			prep.addBatch();
		}
		connection.setAutoCommit(false);
		prep.executeBatch();
		connection.setAutoCommit(true);
		
	}
	//将match文件中的球员赛场成绩记录添加到数据库中
	private void storePlayerMatchPerformance(File[] fileList)throws Exception{
		
		int row = 0;
		for(File file : fileList){
			row = storePlayerMatchPerformance(file,row);
		}
		
		storePlayerMatchPerformance();
		
	}
	
	//将单个文件中的球员赛场成绩添加到PreparedStatement中，并在PreparedStatement中数据过多时填入数据库中
	private int storePlayerMatchPerformance(File file,int row)throws Exception{
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
		String strTemp = file.getName();
		int matchId = getMatchId(strTemp.split("_")[0],strTemp.split("_")[1],getTeamId(strTemp.split("_")[2].split("-")[0]));
		br.readLine();
		strTemp = br.readLine();//忽略前两行
		int starts = 5;
		int teamId = 0;
		connection.setAutoCommit(false);
		while((strTemp = br.readLine())!=null){
			if(strTemp.split(";").length > 1){
				starts+=storePlayerPerformance(matchId,teamId,strTemp,starts);
				row ++ ;
			}else{
				starts = 5;
				teamId = getTeamId(strTemp);
			}
		}
		connection.setAutoCommit(true);
		br.close();
		return row;
	}
	
	//将一条球员赛场成绩记录添加到PreparedStatement中
	private int storePlayerPerformance(int matchId,int teamId,String record,int starts)throws Exception{
		String[] player = record.split(";");
		int playerId = getPlayerId(player[0]);
		String[] performance = new String[23];
		performance[22] = player[0];
		performance[0] = matchId+"";
		performance[1] = teamId+"";

		if(playerId>0){
			performance[2] = playerId+"";
		}else{
			performance[2] = playerId+"";
		}
		performance[3] = player[1];
		//上场时间
		if(player[2].equals("null")||(player[2].equals("None"))){
			player[2] = "0:0";
		}
		performance[4]=(Integer.parseInt(player[2].split(":")[0])*60+Integer.parseInt(player[2].split(":")[1]))+"";
		
		for(int i = 3;i < 18;i++){
			if(player[i].equals("null")||(player[i].equals("None"))){
				player[i] = "0";
				performance[i+2]="0";
			}else{
				performance[i+2]=player[i];
			}
		}
		int doubles = 0;//计算两双
		for(int i =11;i < 15;i++){
			if((Integer.parseInt(player[i])>9)){
				doubles++;
			}
		}
		if(Integer.parseInt(player[17])>9){
			doubles++;
		}
		if (doubles>1){
			performance[20]="1";
		}else{
			performance[20]="0";
		}
		if(starts>0){
			performance[21]="1";
			this.performance.add(performance);
			return -1;
		}else{
			performance[21]="0";
			this.performance.add(performance);
			return 0;
		}
	}
	
 	public int getPlayerId(String n)throws Exception{
		int playerId = -1;
		Statement stat = connection.createStatement();
		String name = checkPlayerName(n);
		ResultSet rs = stat.executeQuery("select id from players where player_name='"+name+"';");
		if(rs.next()){
			playerId = rs.getInt(1);
		}
		return playerId;
	}
	
	//检查球员姓名中是否含有',将其改成\'
	private String checkPlayerName(String name){
		String result="";
		for(int i = 0 ; i < name.length();i ++){
			if(name.charAt(i)=='\''){
				result+="\'\'";
			}else{
				result+=name.charAt(i);
			}
		}
		return result;
	}
	
	//根据队伍缩写名查找队伍Id，结果返回int类型的Id值
	private int getTeamId(String abb)throws Exception{
		int teamId;
		Statement stat = connection.createStatement();
		ResultSet rs = stat.executeQuery("select id from teams where abbreviation='"+abb+"';");
		teamId = rs.getInt(1);
		return teamId;
	}
	
	//根据赛季，比赛日期和主场队伍查找比赛Id，结果返回int类型的Id
	public int getMatchId(String season,String date,int home_court_team_id)throws Exception{
		int matchId;
		Statement stat = connection.createStatement();
		ResultSet rs = stat.executeQuery("select id from matches where date_of_match='"+
		date+"' and home_court_team_id='"+home_court_team_id+"' and season='"+season+"';");
		matchId = rs.getInt(1);
		return matchId;
	};
	
	public void monitorFiles(String rootDir) throws Exception{
		//String rootDir = "C:/Users/cross/Desktop";
		Class.forName("org.sqlite.JDBC");
    	connection = DriverManager.getConnection("jdbc:sqlite:NBADatabase.db");
		long interval = TimeUnit.SECONDS.toMillis(1);
		FileAlterationObserver observer = new FileAlterationObserver(
				rootDir,FileFilterUtils.and(FileFilterUtils.fileFileFilter()),null);
		observer.addListener(new MyFileListener());
		monitor = new FileAlterationMonitor(interval,observer);
		monitor.start();
	};
	
	public void stopMonitor()throws Exception{
		monitor.stop();
	}
	
	public class MyFileListener extends FileAlterationListenerAdaptor {
		public void onFileCreate(File file){
			PreparedStatement matchPrep;
			try {
				matchPrep = connection.prepareStatement(""
						+ "insert into matches values ("
						+ "?,?,?,?,?,?,?,?,?,?,"
						+ "?,?,?,?,?,?)");
				storeMatches(file,matchPrep);
				connection.setAutoCommit(false);
				matchPrep.executeBatch();
				connection.commit();
				matchPrep.close();
				PreparedStatement overtimePrep = connection.prepareStatement(""
						+ "insert into overtime_matches values("
						+ "?,?,?,?)");
				storeOvertimeMatches(file,overtimePrep);
				connection.setAutoCommit(false);
				overtimePrep.executeBatch();
				connection.commit();
				overtimePrep.close();
				PreparedStatement performancePrep = connection.prepareStatement(""
						+ "insert into player_match_performance values ("
						+ "?,?,?,?,?,?,?,?,?,?,"
						+ "?,?,?,?,?,?,?,?,?,?,"
						+ "?,?,?)");
				storePlayerMatchPerformance(file,0);
				connection.setAutoCommit(false);
				performancePrep.executeBatch();
				connection.commit();
				performancePrep.close();
				Calendar c = Calendar.getInstance();
				System.out.println(c.getTimeInMillis()+file.getName());
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			};
			
			
		}
	}

	public static void main(String[] args) throws Exception{
		Dao dao = new Dao();
		dao.newDatabase();//3秒
		dao.readFiles("C:/Users/cross/Documents/CSE/CSEIII data/迭代一数据/");
		dao.monitorFiles("C:/Users/cross/Documents/CSE/CSEIII data/迭代一数据/matches/");
		//dao.stopMonitor();
		//Class.forName("org.sqlite.JDBC");
    	//dao.connection = DriverManager.getConnection("jdbc:sqlite:NBADatabase.db");
		//System.out.println(dao.getPlayerId("Aaron Brosoks"));
	}

}