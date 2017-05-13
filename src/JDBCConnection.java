import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class JDBCConnection {
	
	Connection con;
	
	public JDBCConnection() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			String connectionUrl = "jdbc:mysql://umik.cb2bxn4cggik.us-west-2.rds.amazonaws.com:3306/umik" ;
			String user = "admin";
			String pass = "Amazonroot";
			this.con = DriverManager.getConnection(connectionUrl, user, pass);
			if (this.con != null) {
			    System.out.println("Connected");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void closeConn() throws SQLException {
		this.con.close();
	}
	
	public ResultSet executeQuery(String query) {
		ResultSet rs = null;
		try {
			Statement st = this.con.createStatement();
			String sql = query;
			rs = st.executeQuery(sql);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return rs;
	}
}
