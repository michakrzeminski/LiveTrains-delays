import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Time;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

public class Stats {
	
	private static JDBCConnection database;
	
	//radius in meters in which train is on stop
	static int stopRadius = 25;
	//minutes before scheduled arrival to check
	static int minutesBeforeArrival = 5;
	//minutes after scheduled arrival to check
	static int minutesAfterArrival = 10;

	//distance in meters/kilometers between two points(lat,lon)
	private double distance(double lat1, double lon1, double lat2, double lon2, char unit) {
		double theta = lon1 - lon2;
	    double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
	    dist = Math.acos(dist);
	    dist = rad2deg(dist);
	    dist = dist * 60 * 1.1515;
	    if (unit == 'K') {
	    	dist = dist * 1.609344;
	    } else if (unit == 'N') {
	    	dist = dist * 0.8684;
	    } else if (unit == 'M') {
	    	dist = dist * 1609.344;
	    }
	    
	    return (dist);
	}
	//conversion radians to degrees
	private double rad2deg(double rad) {
		return (rad * 180.0 / Math.PI);
	}
	 //conversion degrees to radians
	private double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}
	
	//
	private boolean checkIfOnStop(double stop_lat, double stop_lon, double train_lat, double train_lon) {
		double distance = distance(stop_lat, stop_lon, train_lat, train_lon, 'M');
		//System.out.println("distance between train and stop is: " + distance + " m");
		if(distance > stopRadius) {
			return false;
		} else {
			return true;
		}
	}
	
	private List<Integer> findClosestStop( double train_lat, double train_lon) {
		List<Integer> stop_ids = new ArrayList<Integer>();
		try{
			String query = "SELECT DISTINCT stop_id, lat, lon  FROM stop";
			ResultSet rs = database.executeQuery(query);
			
			while(rs.next()) {
				double lat = rs.getDouble("lat");
				double lon = rs.getDouble("lon");
				if(checkIfOnStop(lat, lon, train_lat, train_lon)) {
					stop_ids.add(rs.getInt("stop_id"));
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return stop_ids;
	}
	
	private void calculateLateness(Integer stop_id, int lines, String brigade, String realTime) {
		Map<String, Integer> timetable = new HashMap<String, Integer>();
		try{
			PreparedStatement stmt = database.con.prepareStatement("SELECT timetable_id, time FROM timetable WHERE stop_id= ? AND line_no= ? AND brigade= ? ORDER BY time ASC");
			stmt.setInt(1,stop_id);
			stmt.setInt(2, lines);
			stmt.setString(3, brigade);
			ResultSet rs = stmt.executeQuery();
			
			while(rs.next()) {
				String tempTime = rs.getString("time");
				Integer tempId = rs.getInt("timetable_id");
				timetable.put(tempTime,tempId);
			}
			
			if(!timetable.isEmpty()) {
				List<String> times = new ArrayList<String>(timetable.keySet());
				String plannedTime = narrowingDownTime(times, realTime);
	
				if(plannedTime != null) {
					int[] lateness = timeBetween(plannedTime, realTime);
					if(lateness[0] == 0 && lateness[1] < 30) {
						int timetable_id = timetable.get(plannedTime);
						//save to database : timetable_id, arrival_time, delay
						PreparedStatement selectstmt = database.con.prepareStatement("SELECT delay FROM delay WHERE timetable_id= ? ");
						selectstmt.setInt(1,timetable_id);
						ResultSet result = selectstmt.executeQuery();
						
						while(result.next()) {
							//timetable_id exists so update and make average
							String old_delay = result.getString("delay");
							String[] temp = old_delay.split(":");
							int[] old = {Integer.parseInt(temp[0]),Integer.parseInt(temp[1]),Integer.parseInt(temp[2])};
							int[] new_delay = makeavarage(old, lateness);
							PreparedStatement update = database.con.prepareStatement("UPDATE delay SET delay = ? WHERE timetable_id = ?");
							String new_late = new_delay[0] + ":" + new_delay[1] + ":" + new_delay[2];
							update.setString(1, new_late);
							update.setInt(2,timetable_id);
							update.executeUpdate();
							return;
						}
						
						//timetable_id not existing so insert
						PreparedStatement insert = database.con.prepareStatement("INSERT INTO delay VALUES ( ?, ?, ? )");
						insert.setInt(1,timetable_id);
						insert.setString(2, realTime);
						String late = lateness[0] + ":" + lateness[1] + ":" + lateness[2];
						insert.setString(3, late);
						insert.executeUpdate();
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String narrowingDownTime(List<String> timetable, String realTime) {
		String temp[] = realTime.split(" ");
		String day = temp[0];
		String time = temp[1];
		String[] realTimeSplit = time.split(":");
		int rt = Integer.parseInt(realTimeSplit[0])*10000 + Integer.parseInt(realTimeSplit[1])*100 + Integer.parseInt(realTimeSplit[2]);
		int min = Integer.MAX_VALUE;
		int index = -1;
		
		for(int i=0;i<timetable.size();i++) {
			String[] timetableSplit = timetable.get(i).split(":");
			int tt = Integer.parseInt(timetableSplit[0])*10000 + Integer.parseInt(timetableSplit[1])*100 + Integer.parseInt(timetableSplit[2]);
			int dist =rt-tt;
			if(dist <= min && dist>0) {
				min = dist;
				index = i;
			}
		}
		
		if(index != -1) {
			return timetable.get(index); 
		} else
			return null;
	}
	
	private int[] timeBetween(String planned, String real) {
		int[] ret = new int[3];
		String[] temp = planned.split(":");
		String[] realTime = real.split(" ");
		String[] temp2 = realTime[1].split(":");
		int distHour = Math.abs(Integer.parseInt(temp[0]) - Integer.parseInt(temp2[0]));
		ret[0] = distHour;
		int distMin = Math.abs(Integer.parseInt(temp[1]) - Integer.parseInt(temp2[1]));
		ret[1] = distMin;
		int distSec = Math.abs(Integer.parseInt(temp[2]) - Integer.parseInt(temp2[2]));
		ret[2] = distSec;
		return ret;
	}
	
	private int[] makeavarage(int[] old_delay, int[] new_delay) {
		int[] ret = new int[3];
		int fir = old_delay[0]*3600 + old_delay[1]*60 + old_delay[2];
		int sec = new_delay[0]*3600 + new_delay[1]*60 + new_delay[2];
		int average = (fir + sec)/2;
		ret[0] = (int) Math.floor(average / 3600);
		average -= (ret[0] * 3600);
		ret[1] = (int) Math.floor(average / 60);
		average -= (ret[1] * 60);
		ret[2] = average;
		return ret;
	}
	
	public static void main(String[] args) {
		Stats stat = new Stats();
		
		database = new JDBCConnection();
		
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate localDate = LocalDate.now();
		String currentDate = dtf.format(localDate);
		System.out.println("Today is: "+currentDate);
		
		try{
			//select from timetable only for current date
			PreparedStatement stmt = database.con.prepareStatement("SELECT DISTINCT time_stamp, lon, line, lat, brigade FROM train_history WHERE time_stamp LIKE ? ");
			stmt.setString(1,currentDate+'%');
			ResultSet rs = stmt.executeQuery();

			int counter=0;
			while(rs.next()) {
				counter++;
				System.out.println(counter);
				int lines = rs.getInt("line");
				String brigade = rs.getString("brigade");
				String time = rs.getString("time_stamp");
				
				String train_lat = rs.getString("lat");
				double train_latDouble;
				String[] tempLat = train_lat.split(",");
				if(tempLat.length > 1) {
					train_latDouble = Double.parseDouble(tempLat[0]+'.'+tempLat[1]);
				} else {
					train_latDouble = Double.parseDouble(train_lat);
				}
				
				String train_lon = rs.getString("lon");
				double train_lonDouble;
				String[] tempLon = train_lon.split(",");
				if(tempLat.length > 1) {
					train_lonDouble = Double.parseDouble(tempLon[0]+'.'+tempLon[1]);
				} else {
					train_lonDouble = Double.parseDouble(train_lon);
				}
				
				List<Integer> stop_ids = new ArrayList<Integer>();
				stop_ids = stat.findClosestStop(train_latDouble, train_lonDouble);
				if(!stop_ids.isEmpty()) {
					for(int i=0;i<stop_ids.size();i++) {
						stat.calculateLateness(stop_ids.get(i), lines, brigade, time);	
					}
				}
			}
			System.out.println(counter);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
