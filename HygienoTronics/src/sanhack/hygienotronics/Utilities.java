package sanhack.hygienotronics;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.DatePicker;

public class Utilities {
	
	private static final String Tag = "MotionDetectionActivity";
	
	public static String getHTTP(String url) throws ClientProtocolException, IOException {
		String result = "";    	
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet httpget = new HttpGet(url);
		ResponseHandler<String> responseHandler = new BasicResponseHandler();			

		// Execute HTTP Get Request
		result = httpclient.execute(httpget, responseHandler);
		return result;
	}

	public static String dateToStr(Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");		
		return sdf.format(date);
	}
	
	public static String parseDate(DatePicker datepicker) {		
		Date date = new Date(datepicker.getYear() - 1900, datepicker.getMonth(), datepicker.getDayOfMonth());
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");		
		return sdf.format(date);
	}
	

	public static Date parseDate(String dateStr) throws ParseException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");	
		return sdf.parse(dateStr);
	}

public static int getIndexOf(String string, String[] stringArray) {
	if(stringArray.length == 0)
		return -1;
	
	for(int i = 0; i < stringArray.length; ++i)
		if(stringArray[i].equals(string))
			return i;
	
	return -1;
}

public static void showAlert(String title, String message, int iconId, Context context) {
	try {
	AlertDialog.Builder builder = new AlertDialog.Builder(context);
	
	builder.setIcon(iconId);
	builder.setTitle(title);
	builder.setMessage(message);
	//builder.setCancelable(false);
	     
	AlertDialog alert = builder.create();
	alert.show();
	}catch(Exception e) {
		Log.e(Tag,"Alert Error : " + e.getMessage());
	}
}

public static double sumDeposits(String deposits) {
	String[] vals = deposits.split(",");
	double sum = 0;
	for (String s : vals)
		try {
			sum += Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {

		}

	return sum;
}
}
