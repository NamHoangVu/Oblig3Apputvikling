package com.example.oblig3s375045s375063;

import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private Handler handler;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-edge layout handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialiserer handler og executor
        handler = new Handler();
        executor = Executors.newSingleThreadExecutor();

        // Konfigurer Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this); // Kartet åpnes og `onMapReady` blir kalt når klart
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Sett en standard posisjon og zoom (for eksempel Oslo)
        LatLng oslo = new LatLng(59.911491, 10.757933);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(oslo, 10));

        // Legger til en klikklytter for å håndtere å legge til nye markører
        mMap.setOnMapClickListener(new OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                åpneLeggTilStedDialog(latLng);
            }
        });

        // Kall metoden for å legge til markører når kartet er klart
        utførWebtjenesteForespørsel();
    }

    private void åpneLeggTilStedDialog(LatLng latLng) {
        // Inflater den egendefinerte dialoglayouten
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_location, null);
        EditText beskrivelseInput = dialogView.findViewById(R.id.descriptionInput);
        EditText adresseInput = dialogView.findViewById(R.id.addressInput);

        new AlertDialog.Builder(this)
                .setTitle("Legg til sted")
                .setView(dialogView)
                .setPositiveButton("Lagre", (dialog, which) -> {
                    String beskrivelse = beskrivelseInput.getText().toString();
                    String adresse = adresseInput.getText().toString();
                    leggTilStedIDatabase(latLng, beskrivelse, adresse);
                })
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void leggTilStedIDatabase(LatLng latLng, String beskrivelse, String adresse) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://dave3600.cs.oslomet.no/~s375045/jsonin.php");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String postData = "beskrivelse=" + beskrivelse + "&gateadresse=" + adresse
                        + "&latitude=" + latLng.latitude + "&longitude=" + latLng.longitude;

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(postData);
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    handler.post(() -> {
                        // Legg til markør på kartet etter at data er lagret
                        mMap.addMarker(new MarkerOptions().position(latLng).title(beskrivelse).snippet(adresse));
                        Toast.makeText(MainActivity.this, "Sted lagret", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    handler.post(() -> Toast.makeText(MainActivity.this, "Feil: " + responseCode, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "Feil: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void utførWebtjenesteForespørsel() {
        executor.execute(() -> {
            try {
                URL url = new URL("https://dave3600.cs.oslomet.no/~s375045/jsonout.php");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                reader.close();

                String response = stringBuilder.toString();
                handler.post(() -> parseOgVisMarkører(response));
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(MainActivity.this, "Feil: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void parseOgVisMarkører(String response) {
        // Sjekk at mMap er initialisert før du prøver å bruke det
        if (mMap == null) {
            Toast.makeText(this, "Kartet er ikke klart ennå", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(response);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String beskrivelse = jsonObject.getString("beskrivelse");
                String adresse = jsonObject.getString("gateadresse");

                // Konverter latitude og longitude fra String til double
                double latitude = Double.parseDouble(jsonObject.getString("latitude"));
                double longitude = Double.parseDouble(jsonObject.getString("longitude"));

                LatLng latLng = new LatLng(latitude, longitude);
                mMap.addMarker(new MarkerOptions().position(latLng).title(beskrivelse).snippet(adresse));
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Feil i parsing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
