package smu.ac.kr.johnber.opendata;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import smu.ac.kr.johnber.R;
import smu.ac.kr.johnber.opendata.APImodel.RunningCourse;
import smu.ac.kr.johnber.opendata.network.ApiService;
import smu.ac.kr.johnber.opendata.network.ApiServiceGenerator;
import smu.ac.kr.johnber.util.BitmapUtil;

import static smu.ac.kr.johnber.util.LogUtils.LOGD;
import static smu.ac.kr.johnber.util.LogUtils.makeLogTag;

public class CourseRequest {
    private static String TAG = makeLogTag(CourseRequest.class);


    private static final int pageNo = 1;
//    private static final int listNo = 500;
    private static final int listNo = 800;
    private static final String responseType = "json";

    private Realm mRealm;
    private JSONObject jsonObject;
    private Context context;

    public CourseRequest(Context context) {
        this.context = context;
    }

    public void loadCourseData(){
        ApiService apiService =
                ApiServiceGenerator.getApiServiceGenerator(ApiServiceGenerator.BASE_URL_TYPE_RUNNINGCOURSE)
                        .getApiService();

        final Call<ResponseBody> reponse =
                apiService.loadRunningCourse(ApiService.DATAGOKR_API_SERVICE_KEY,pageNo,listNo,responseType);

        reponse.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                LOGD(TAG, "server contacted at: " + call.request().url());
                Realm.init(context);
                mRealm = Realm.getDefaultInstance();

                try {
                    //response body를 string으로 변환
                    String json = response.body().string();
                    JSONArray jsonArray = new JSONArray(json);
                    for (int i = 0; i< jsonArray.length();i++){
                        //Array안의 각 코스 Ojbect를 꺼낸다.
                        jsonObject = jsonArray.getJSONObject(i);
                        //TODO:
                        if(!jsonObject.get("총길이").toString().equals("null")
                                &&!jsonObject.get("총소요시간").toString().equals("null")
                                &&jsonObject.get("길소개").toString().length()>50){
                        insertCourseData();}
                    }
                    LOGD(TAG, "category: "+jsonArray.length()
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                LOGD(TAG, "inserted : " + getCourseListSize());

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                LOGD(TAG, "server failed at: " + call.request().url() );
            }
        });
    }

    private void insertCourseData() {
        mRealm.beginTransaction();
        Number currentIdNum = mRealm.where(RunningCourse.class).max("id");
        int nextId;
        if(currentIdNum == null) {
            nextId = 1;
        } else {
            nextId = currentIdNum.intValue() + 1;
        }
        RunningCourse runningCourse = mRealm.createObject(RunningCourse.class);
        //RunningCourse 객체에 매핑

        try {
            runningCourse.setId(nextId);
            runningCourse.setEndPoint(jsonObject.get("종료지점명").toString());
            runningCourse.setStartPoint(jsonObject.get("시작지점명").toString());
            runningCourse.setCourse(jsonObject.get("경로정보").toString());
            runningCourse.setCourseInfo(jsonObject.get("길소개").toString());
            runningCourse.setDistance(jsonObject.get("총길이").toString());
            runningCourse.setCourseName(jsonObject.get("길명").toString());
            runningCourse.setTime(jsonObject.get("총소요시간").toString());
            runningCourse.setStartPointAddr(jsonObject.get("시작지점소재지지번주소").toString());
            runningCourse.setStartPointRoadAddr(jsonObject.get("시작지점도로명주소").toString());
            runningCourse.setEndPointAddr(jsonObject.get("종료지점소재지지번주소").toString());
            runningCourse.setEndPointRoadAddr(jsonObject.get("종료지점소재지도로명주소").toString());
//            LatLng sLat = this.getLatLangFromAddr(runningCourse).get(0);
//            LatLng eLat = this.getLatLangFromAddr(runningCourse).get(1);
//            runningCourse.setsLat(sLat.latitude);
//            runningCourse.setsLng(sLat.longitude);
//            runningCourse.seteLat(eLat.latitude);
//            runningCourse.seteLng(eLat.longitude);
        } catch (JSONException e) {}
        mRealm.commitTransaction();
    }

    private int getCourseListSize(){
        RealmResults<RunningCourse> list = mRealm.where(RunningCourse.class).findAll();
        return list.size();
    }

    private List<LatLng> getLatLangFromAddr(RunningCourse mcourseData) {
        List<LatLng> latlng = new ArrayList<>();
        if(mcourseData == null)
        LOGD(TAG,"mcourseData is empty");
        try {
            //시작지점명 , 종료지점명으로부터 위도,경도 정보 알아내기
            Geocoder mGeoCoder = new Geocoder(context, Locale.KOREA);
            List<Address> startLocation = null;
            List<Address> endLocation = null;
            if (!(mcourseData.getStartPointAddr().equals("null")||mcourseData.getStartPointAddr().equals("해당 없음"))) {
                //지번주소
                LOGD(TAG, "Course data sp " + mcourseData.getStartPointAddr());
                startLocation = mGeoCoder.getFromLocationName(mcourseData.getStartPointAddr(), 1);
                LOGD(TAG, "start : " + mGeoCoder.getFromLocationName(mcourseData.getStartPointAddr(), 1).toString());
            } else if (!(mcourseData.getStartPointRoadAddr().equals("null")||mcourseData.getStartPointRoadAddr().equals("해당 없음"))) {
                //도로명주소
                LOGD(TAG, "Course data srRp " + mcourseData.getStartPointRoadAddr());
                startLocation = mGeoCoder.getFromLocationName(mcourseData.getStartPointRoadAddr(), 1);
                LOGD(TAG, "start : " + mGeoCoder.getFromLocationName(mcourseData.getStartPointRoadAddr(), 1).toString());
            }

            if (!(mcourseData.getEndPointAddr().equals("null")||mcourseData.getEndPointAddr().equals("해당 없음"))) {
                //지번주소
                LOGD(TAG, "Course ep " + mcourseData.getEndPointAddr());
                endLocation = mGeoCoder.getFromLocationName(mcourseData.getEndPointAddr(), 1);
                LOGD(TAG, "end : " + mGeoCoder.getFromLocationName(mcourseData.getEndPointAddr(), 1).toString());
                if (endLocation.size() <= 0) {
                    endLocation = startLocation;
                }
            } else if (!(mcourseData.getEndPointRoadAddr().equals("null")||mcourseData.getEndPointRoadAddr().equals("해당 없음"))) {
                //도로명주소
                LOGD(TAG, "Course data eRp " + mcourseData.getEndPointRoadAddr());
                endLocation = mGeoCoder.getFromLocationName(mcourseData.getEndPointRoadAddr(), 1);
                LOGD(TAG, "end : " + mGeoCoder.getFromLocationName(mcourseData.getEndPointRoadAddr(), 1).toString());
            }
            LatLng start =new LatLng(startLocation.get(0).getLatitude(), startLocation.get(0).getLongitude());
            LatLng end =new LatLng(endLocation.get(0).getLatitude(), endLocation.get(0).getLongitude());
            latlng.add(start);
            latlng.add(end);
            LOGD(TAG, "size of lsitsts : " + latlng.size());


        } catch (IOException e) {
            e.printStackTrace();
            LOGD(TAG, "cannot find location info" + mcourseData.getCourseName() + " " + mcourseData.getStartPointAddr() + " " + mcourseData.getStartPointRoadAddr() + " " + mcourseData.getEndPointAddr() + " " + mcourseData.getEndPointRoadAddr());
        }

        return latlng;
    }


}