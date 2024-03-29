package ufc.pet.bustracker;


import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import ufc.pet.bustracker.tools.CustomJsonArrayRequest;
import ufc.pet.bustracker.tools.CustomJsonObjectRequest;
import ufc.pet.bustracker.tools.JSONParser;
import ufc.pet.bustracker.ufc.pet.bustracker.types.Bus;
import ufc.pet.bustracker.ufc.pet.bustracker.types.Route;

public class MapActivity extends AppCompatActivity implements
        View.OnClickListener,
        OnMapReadyCallback,
        GoogleMap.OnPolylineClickListener
{

    // Tag para os logs
    public static final String TAG = MapActivity.class.getName();

    // Elementos da interface
    private GoogleMap mMap;
    private Toolbar mToolbar;
    private TextView mInfoTitle;
    private TextView mInfoDescription;
    private Button mUpdateButton;
    private ArrayList<Route> routes;
    private ArrayList<Bus> buses;
    private ArrayList<Marker> busOnScreen;
    private ProgressDialog progressDialog;

    // Gerenciador de conectividade
    private RequestQueue requestQueue;
    private String serverPrefix;
    private String token;

    // Handler para lidar com atualização/notificação automática
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        SharedPreferences pref = getSharedPreferences(getString(R.string.preferences), MODE_PRIVATE);
        token = pref.getString(getString(R.string.token), "null");

        // Localiza elementos da interface
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mInfoTitle = (TextView) findViewById(R.id.info_title);
        mInfoDescription = (TextView) findViewById(R.id.info_description);
        mUpdateButton = (Button) findViewById(R.id.update_button);

        requestQueue = Volley.newRequestQueue(getApplicationContext());
        routes = new ArrayList<>(0);
        buses = new ArrayList<>(0);
        busOnScreen = new ArrayList<>();
        serverPrefix = getResources().getString(R.string.host_prefix);

        // Configura elementos da interface
        setSupportActionBar(mToolbar);
        mUpdateButton.setOnClickListener(this);

        // Atribui mapa ao elemento fragment na interface
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        String firebase = pref.getString(getString(R.string.firebase), "null");
        boolean teste_firebase = pref.getBoolean(getString(R.string.firebase_on), false);
        if(firebase.equals("null") || !teste_firebase) {
            Log.e("If", firebase + "boolean "+ String.valueOf(teste_firebase));
            Handler handler2 =  new Handler();
            handler2.postDelayed(firebaseTokenGetter, 3000);
        }

        progressDialog = ProgressDialog.show(MapActivity.this, "Aguarde...",
                "Carregando informações");
        getRoutesFromServer();


    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.update_button:
                progressDialog = ProgressDialog.show(MapActivity.this, "Aguarde...",
                        "Carregando informações");
                getRoutesFromServer();
                break;
        }
    }

    /**
     * Ações ao clicar em um polyline
     */
    public void onPolylineClick(Polyline p){
        int selected = ContextCompat.getColor(getApplicationContext(), R.color.colorAccent);
        int unselected = ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary);
        for(Route r : routes){
            if(r.isActiveOnMap()){
                Polyline routePoly = r.getAssociatedPolyline();
                if(routePoly.hashCode() == p.hashCode()) {
                    mInfoTitle.setText(r.getName());
                    mInfoDescription.setText(r.getDescription());
                } else {
                    routePoly.setColor(unselected);
                }
            }
        }
        p.setColor(selected);
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        for(LatLng l : p.getPoints()){
            b.include(l);
        }
        LatLngBounds bounds = b.build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60));
        getBusesOnRoute(86);
        handler.postDelayed(updateBus, 0);
    }

    public void getRoutesFromServer() {
        String url = serverPrefix + "/routes/86"; // apenas uma rota por que só ela está atualizada com ônibus
        //token para teste
        JsonObjectRequest jreq = new CustomJsonObjectRequest(JsonObjectRequest.Method.GET, url, null,token ,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        JSONParser parser = new JSONParser();
                        try {

                            Route r = parser.parseRoute(response);
                            routes.add(r);
                            progressDialog.dismiss();
                            drawRoutesOnMap();
                        } catch (Exception e){
                            Log.e(MapActivity.TAG, e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("fuuudeu", error.toString());
                        progressDialog.dismiss();
                    }
                });
        requestQueue.add(jreq);
    }

    public void getBusesOnRoute(int id) {
        String url = serverPrefix + "/routes/" + id + "/buses?localizations=1";
        //token para teste
        JsonArrayRequest jreq = new CustomJsonArrayRequest(JsonArrayRequest.Method.GET, url, null, token,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        JSONParser parser = new JSONParser();
                        try {
                            buses.clear();
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject ob = response.getJSONObject(i);
                                Bus b = parser.parseBus(ob);
                                buses.add(b);
                            }
                            markBusesOnMap();
                        } catch (Exception e) {
                            Log.e(MapActivity.TAG, e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(MapActivity.TAG, error.getMessage());
                    }
                });
        requestQueue.add(jreq);
    }

    /**
     * Desenha as rotas e armazena os polylines associados
     */
    public void drawRoutesOnMap(){
        for(Route r : routes){
            if (r.isActiveOnMap())
                return;
            Polyline p = mMap.addPolyline(
                    new PolylineOptions()
                            .addAll(r.getPoints())
                            .clickable(true)
                            .color(ContextCompat.getColor(
                                    getApplicationContext(),
                                    R.color.colorPrimary))
            );
            r.setAssociatedPolyline(p);
        }
    }

    /**
     * Marca os ônibus no mapa de acordo com os que estão ativos, dado o vetor buses
     * que é preenchido no recebimento da informação do servidor na funçao getBusesOnRoute.
     */
    public void markBusesOnMap() {
        if(busOnScreen.size() != 0){
            for(int i = 0; i < busOnScreen.size(); i++){
                busOnScreen.get(i).remove();
            }
            busOnScreen.clear();

        }
        for (Bus b : buses) {
            Marker m = mMap.addMarker(
                    new MarkerOptions()
                            .position(b.getCoordinates())
                            .title("Ônibus " + b.getId())
            );
            m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marcador));
            busOnScreen.add(m);
            b.setAssociatedMarker(m);
        }
    }

    /**
     * Atualizar os ônibus automaticamente no mapa
     */
    Runnable updateBus = new Runnable(){
        @Override
        public void run(){
            for(Route r : routes){
                if(r.isActiveOnMap()) {
                    getBusesOnRoute(r.getId_routes());
                }
            }
            // Verifica o tempo de atualização definido pelo usuário em configurações
            SharedPreferences pref = getSharedPreferences(getString(R.string.preferences), MODE_PRIVATE);
            int update_time = pref.getInt(getString(R.string.update_time), 3);
            handler.postDelayed(updateBus, (update_time * 1000));
        }
    };

    Runnable firebaseTokenGetter = new Runnable(){
        @Override
        public void run(){
            SharedPreferences pref = getSharedPreferences(getString(R.string.preferences), MODE_PRIVATE);
            String firebase = pref.getString(getString(R.string.firebase), "null");
            sendToken(firebase);
        }
    };
    /**
     * Fornece um manipulador para o objeto do mapa
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnPolylineClickListener(this);
        // Posiciona o mapa em Sobral
        LatLng sobral = new LatLng(-3.6906438,-40.3503957);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(sobral, 15));
    }

    /**
     * Cria um menu de opções na barra superior
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }


    public void onClickAbout(MenuItem item){
        startActivity(new Intent(MapActivity.this, AboutActivity.class));
    }


    public void onClickSettings(MenuItem item){
        startActivity(new Intent(MapActivity.this, SettingsActivity.class));;
    }

    public void onClickNotifications(MenuItem item){
        startActivity(new Intent(MapActivity.this, NotificationListActivity.class));;
    }

    @Override
    public void onResume(){
        super.onResume();
        handler.postDelayed(updateBus, 250);
    }

    /**
     * Funções para garantir que o thread updateBus pare quando o usuário
     * sair da activity do Mapa
     */
    @Override
    public void onPause(){
        super.onPause();
        handler.removeCallbacks(updateBus);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        handler.removeCallbacks(updateBus);
    }

    /**
     * Registra o usuário na lista de mensagens de uma rota para receber notificações
     * da mesma
     * @param token_firebase token pegue em FirebaseIDService
     */
    public void sendToken(String token_firebase){
        String server = getString(R.string.host_prefix) + "/routes/86/messages/register";
        SharedPreferences pref = getSharedPreferences(getString(R.string.preferences), MODE_PRIVATE);
        String token = pref.getString(getString(R.string.token), "null");

        JSONObject dado = null;
        try{
            dado = new JSONObject("{\"registration_token_firebase\": \""+token_firebase+"\"}");
        }
        catch(JSONException e){
            Log.e("deu erro", "no token firebase");
        }

        JsonObjectRequest request = new CustomJsonObjectRequest(Request.Method.POST, server, dado, token,
                new Response.Listener<JSONObject>(){
                    @Override
                    public void onResponse(JSONObject Response){
                        Log.i("Registro deu certo!", "No firebase pra mensanges");
                        SharedPreferences pref = getSharedPreferences(getString(R.string.preferences), MODE_PRIVATE);
                        SharedPreferences.Editor edit = pref.edit();
                        edit.putBoolean(getString(R.string.firebase_on), true);
                        edit.apply();

                    }
                },
                new Response.ErrorListener(){
                    @Override
                    public void onErrorResponse(VolleyError error){
                        Log.e("Erro em Registro", error.toString());
                    }
                });
        requestQueue.add(request);
    }
}
