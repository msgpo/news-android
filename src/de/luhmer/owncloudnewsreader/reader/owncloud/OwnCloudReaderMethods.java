package de.luhmer.owncloudnewsreader.reader.owncloud;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;

import com.google.gson.stream.JsonReader;

import de.luhmer.owncloudnewsreader.database.DatabaseConnection;
import de.luhmer.owncloudnewsreader.reader.FeedItemTags;
import de.luhmer.owncloudnewsreader.reader.FeedItemTags.TAGS;
import de.luhmer.owncloudnewsreader.reader.HttpJsonRequest;

public class OwnCloudReaderMethods {
	public static String maxSizePerSync = "200";
	
	public static int GetUpdatedItems(TAGS tag, Context cont, long lastSync, API api) throws Exception
	{
		List<NameValuePair> nVPairs = new ArrayList<NameValuePair>();
		//nVPairs.add(new BasicNameValuePair("batchSize", maxSizePerSync));
		if(tag.equals(TAGS.ALL_STARRED))
		{
			nVPairs.add(new BasicNameValuePair("type", "2"));
			nVPairs.add(new BasicNameValuePair("id", "0"));
		}
		else if(tag.equals(TAGS.ALL))
		{			
			nVPairs.add(new BasicNameValuePair("type", "3"));
			nVPairs.add(new BasicNameValuePair("id", "0"));
		}
		nVPairs.add(new BasicNameValuePair("lastModified", String.valueOf(lastSync)));
				
		
    	InputStream is = HttpJsonRequest.PerformJsonRequest(api.getItemUpdatedUrl(), nVPairs, api.getUsername(), api.getPassword(), cont);
    	
		DatabaseConnection dbConn = new DatabaseConnection(cont);
        try
        {
        	return readJsonStream(is, new InsertItemIntoDatabase(dbConn));
        } finally {        	
        	dbConn.closeDatabase();
        	is.close();
        }
	}
	
	//"type": 1, // the type of the query (Feed: 0, Folder: 1, Starred: 2, All: 3)
	public static int GetItems(TAGS tag, Context cont, String offset, boolean getRead, String id, String type, API api) throws Exception
	{
		List<NameValuePair> nVPairs = new ArrayList<NameValuePair>();
		nVPairs.add(new BasicNameValuePair("batchSize", maxSizePerSync));
		if(tag.equals(TAGS.ALL_STARRED))
		{
			nVPairs.add(new BasicNameValuePair("type", type));
			nVPairs.add(new BasicNameValuePair("id", id));
		}
		else if(tag.equals(TAGS.ALL))
		{			
			nVPairs.add(new BasicNameValuePair("type", type));
			nVPairs.add(new BasicNameValuePair("id", id));
		}
		nVPairs.add(new BasicNameValuePair("offset", offset));
		if(getRead)
			nVPairs.add(new BasicNameValuePair("getRead", "true"));		 
		else
			nVPairs.add(new BasicNameValuePair("getRead", "false"));
		
		
		InputStream is = HttpJsonRequest.PerformJsonRequest(api.getItemUrl(), nVPairs, api.getUsername(), api.getPassword(), cont);
		
		DatabaseConnection dbConn = new DatabaseConnection(cont);
        try
        {
        	return readJsonStream(is, new InsertItemIntoDatabase(dbConn));
        } finally {        	
        	dbConn.closeDatabase();
        	is.close();
        }
	}
	
	
	public static int GetFolderTags(Context cont, API api) throws Exception
	{	
		InputStream is = HttpJsonRequest.PerformJsonRequest(api.getFolderUrl(), null, api.getUsername(), api.getPassword(), cont);
		DatabaseConnection dbConn = new DatabaseConnection(cont);
		int result = 0;
		try
        {
			InsertFolderIntoDatabase ifid = new InsertFolderIntoDatabase(dbConn);
			result = readJsonStream(is, ifid);
			ifid.WriteAllToDatabaseNow();
        } finally {        	
        	dbConn.closeDatabase();
        	is.close();        	
        }
		
		return result;
	}
	
	public static int GetFeeds(Context cont, API api) throws Exception
	{
		InputStream inputStream = HttpJsonRequest.PerformJsonRequest(api.getFeedUrl() , null, api.getUsername(), api.getPassword(), cont);

		DatabaseConnection dbConn = new DatabaseConnection(cont);
		int result = 0;
		try {
			InsertFeedIntoDatabase ifid = new InsertFeedIntoDatabase(dbConn);
			result = readJsonStream(inputStream, ifid);
			ifid.WriteAllToDatabaseNow();
		} finally {
			dbConn.closeDatabase();
			inputStream.close();
		}
		return result;
	}
	
	/**
	 * can parse json like {"items":[{"id":6782}]}
	 * @param in
	 * @param iJoBj
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 */
	public static int readJsonStream(InputStream in, IHandleJsonObject iJoBj) throws IOException, JSONException {
		int count = 0;
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        reader.beginObject();
        reader.nextName();
        reader.beginArray();
        while (reader.hasNext()) {
        	reader.beginObject();
        	
        	JSONObject e = getJSONObjectFromReader(reader);
        	
        	iJoBj.performAction(e);        	
    		
    		reader.endObject();
    		count++;
        }
        reader.endArray();        
        reader.close();
        
        return count;
    }
	
	/**
	 * can read json like {"version":"1.101"}
	 * @param in
	 * @param iJoBj
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 */
	private static int readJsonStreamSimple(InputStream in, IHandleJsonObject iJoBj) throws IOException, JSONException {
		int count = 0;
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        reader.beginObject();
        	
        JSONObject e = getJSONObjectFromReader(reader);
        	
        iJoBj.performAction(e);        	
    	
        reader.endObject();        
        reader.close();
        
        return count;
    }

	private static JSONObject getJSONObjectFromReader(JsonReader jsonReader) {
		JSONObject jObj = new JSONObject();
		try {
			while(jsonReader.hasNext()) {
				try {					
					jObj.put(jsonReader.nextName(), jsonReader.nextString());
				} catch(Exception ex) {					
					//ex.printStackTrace();
					jsonReader.skipValue();
				}
			}
			return jObj;
		} catch (Exception e) {			
			e.printStackTrace();
		}
		return null;
	}

	
	
	public static boolean PerformTagExecutionAPIv2(List<String> itemIds, FeedItemTags.TAGS tag, Context context, API api)
	{	        
        String jsonIds = null;
        
        
		String url = api.getTagBaseUrl(); 
		if(tag.equals(TAGS.MARK_ITEM_AS_READ) || tag.equals(TAGS.MARK_ITEM_AS_UNREAD))
        {
			jsonIds = buildIdsToJSONArray(itemIds);
	        
	        if(tag.equals(TAGS.MARK_ITEM_AS_READ))
	        	url += "read/multiple";
	        else
	        	url += "unread/multiple";
        } else {
            DatabaseConnection dbConn = new DatabaseConnection(context);
            
            HashMap<String, String> items = new HashMap<String, String>();
            for(String idItem : itemIds)
            {
	            Cursor cursor = dbConn.getArticleByID(dbConn.getRowIdOfFeedByItemID(idItem));
	            //Cursor cursor = dbConn.getFeedByID itemID);
	            cursor.moveToFirst();
	
	            String idSubscription = cursor.getString(cursor.getColumnIndex(DatabaseConnection.RSS_ITEM_SUBSCRIPTION_ID));
	            String guidHash = cursor.getString(cursor.getColumnIndex(DatabaseConnection.RSS_ITEM_GUIDHASH));
	            
	            cursor.close();
	            
	            String subscription_id = dbConn.getSubscriptionIdByRowID(idSubscription);
	            //url += subscription_id;
	            
	            items.put(guidHash, subscription_id);
            }
            dbConn.closeDatabase();
            
            jsonIds = buildIdsToJSONArrayWithGuid(items);
            /*
	        if(jsonIds != null)
	        {
	            nameValuePairs = new ArrayList<NameValuePair>();
	            nameValuePairs.add(new BasicNameValuePair("itemIds", jsonIds));
	        }*/
            
            if(tag.equals(TAGS.MARK_ITEM_AS_STARRED))
                url += "star/multiple";
            else if(tag.equals(TAGS.MARK_ITEM_AS_UNSTARRED))
                url += "unstar/multiple";
            
            
            /*
            url += "/" + guidHash;

            if(tag.equals(TAGS.MARK_ITEM_AS_STARRED))
                url += "/star";
            else if(tag.equals(TAGS.MARK_ITEM_AS_UNSTARRED))
                url += "/unstar";
            */
            
        }
        try
        {
		    int result = HttpJsonRequest.performTagChangeRequest(url, api.getUsername(), api.getPassword(), context, jsonIds);
		    //if(result != -1 || result != 405)
		    if(result == 200)
    			return true;
    		else
    			return false;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return false;
        }
	}
	
	public static boolean PerformTagExecutionAPIv1(String itemId, FeedItemTags.TAGS tag, Context context, API api)
	{
		String url = api.getTagBaseUrl(); 
		if(tag.equals(TAGS.MARK_ITEM_AS_READ) || tag.equals(TAGS.MARK_ITEM_AS_UNREAD))
        {
			if(tag.equals(TAGS.MARK_ITEM_AS_READ))
				url += itemId + "/read";
			else
				url += itemId + "/unread";
        } else {
            DatabaseConnection dbConn = new DatabaseConnection(context);           
                        
            Cursor cursor = dbConn.getArticleByID(dbConn.getRowIdOfFeedByItemID(itemId));            
            cursor.moveToFirst();

            String idSubscription = cursor.getString(cursor.getColumnIndex(DatabaseConnection.RSS_ITEM_SUBSCRIPTION_ID));
            String guidHash = cursor.getString(cursor.getColumnIndex(DatabaseConnection.RSS_ITEM_GUIDHASH));            
            cursor.close();
            
            String subscription_id = dbConn.getSubscriptionIdByRowID(idSubscription);
            url += subscription_id;
            
            dbConn.closeDatabase();
            
            url += "/" + guidHash;
            if(tag.equals(TAGS.MARK_ITEM_AS_STARRED))
                url += "/star";
            else if(tag.equals(TAGS.MARK_ITEM_AS_UNSTARRED))
                url += "/unstar";
        }
        try
        {
		    int result = HttpJsonRequest.performTagChangeRequest(url, api.getUsername(), api.getPassword(), context, null);		    
		    if(result == 200)
    			return true;
    		else
    			return false;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return false;
        }
	}

	
	public static String GetVersionNumber(Context cont, String username, String password, String oc_root_path) throws Exception
	{	
		if(oc_root_path.endsWith("/"))
			oc_root_path = oc_root_path.substring(0, oc_root_path.length() - 1);
		
		//Try APIv2
		try {
			String requestUrl = oc_root_path + OwnCloudConstants.ROOT_PATH_APIv2 + OwnCloudConstants.VERSION_PATH;
			InputStream is = HttpJsonRequest.PerformJsonRequest(requestUrl, null, username, password, cont);
			try {
				GetVersion gv = new GetVersion();
				readJsonStreamSimple(is, gv);
				return gv.getVersion();
			} finally {
				is.close();
			}
		} 
		catch(AuthenticationException ex) {
			throw ex;
		} catch(Exception ex) {	//TODO GET HERE THE RIGHT EXCEPTION		
			String requestUrl = oc_root_path + OwnCloudConstants.ROOT_PATH_APIv1 + OwnCloudConstants.VERSION_PATH;
			InputStream is = HttpJsonRequest.PerformJsonRequest(requestUrl, null, username, password, cont);
			try {
				GetVersion gv = new GetVersion();
				readJsonStreamSimple(is, gv);
				return gv.getVersion();
			} finally {
				is.close();
			}
		}
	}
	
	
	
    private static String buildIdsToJSONArray(List<String> ids)
    {
        try
        {
            JSONArray jArr = new JSONArray();
            for(String id : ids)
                jArr.put(Integer.parseInt(id));

            
            JSONObject jObj = new JSONObject();
            jObj.put("items", jArr);
            
            return jObj.toString();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        return null;
    }
    
    private static String buildIdsToJSONArrayWithGuid(HashMap<String, String> items)
    {
        try
        {
            JSONArray jArr = new JSONArray();
            for(Map.Entry<String, String> entry : items.entrySet())
            {
            	JSONObject jOb = new JSONObject();
            	jOb.put("feedId", Integer.parseInt(entry.getValue()));
            	jOb.put("guidHash", entry.getKey());
            	jArr.put(jOb);
            }
            
            JSONObject jObj = new JSONObject();
            jObj.put("items", jArr);
            
            return jObj.toString();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        return null;
    }
}
