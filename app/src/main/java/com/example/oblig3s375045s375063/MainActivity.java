package com.example.oblig3s375045s375063;

import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
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
import com.google.android.gms.maps.model.Marker;
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

        // Tilpass layout for å inkludere systemstenger (f.eks. statuslinje)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialiserer handler for UI-oppdateringer og executor for bakgrunnsoppgaver
        handler = new Handler();
        executor = Executors.newSingleThreadExecutor();

        // Sett opp Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this); // Kartet lastes inn
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Sett en standard posisjon og zoom (her: Oslo)
        LatLng oslo = new LatLng(59.911491, 10.757933);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(oslo, 10));

        // Tilpasset infovindu for markører
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null; // Bruk standard ramme for infovinduet
            }

            @Override
            public View getInfoContents(Marker marker) {
                View infoWindow = LayoutInflater.from(MainActivity.this).inflate(R.layout.custom_info_window, null);
                TextView title = infoWindow.findViewById(R.id.title);
                TextView snippet = infoWindow.findViewById(R.id.snippet);

                title.setText(marker.getTitle());
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Håndter klikk på kartet for å legge til nye markører
        mMap.setOnMapClickListener(latLng -> åpneLeggTilStedDialog(latLng));

        // Last inn eksisterende markører fra databasen
        utførWebtjenesteForespørsel();
    }

    private void åpneLeggTilStedDialog(LatLng latLng) {
        // Inflater dialogen for å legge til et sted
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_location, null);
        EditText nameInput = dialogView.findViewById(R.id.nameInput); // Input for navn
        EditText beskrivelseInput = dialogView.findViewById(R.id.descriptionInput); // Input for beskrivelse
        EditText adresseInput = dialogView.findViewById(R.id.addressInput); // Input for adresse

        new AlertDialog.Builder(this)
                .setTitle("Legg til sted")
                .setView(dialogView)
                .setPositiveButton("Lagre", (dialog, which) -> {
                    String navn = nameInput.getText().toString();
                    String beskrivelse = beskrivelseInput.getText().toString();
                    String adresse = adresseInput.getText().toString();
                    leggTilStedIDatabase(latLng, navn, beskrivelse, adresse);
                })
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void leggTilStedIDatabase(LatLng latLng, String navn, String beskrivelse, String adresse) {
        executor.execute(() -> {
            try {
                // Koble til web-tjeneste for å lagre sted i databasen
                URL url = new URL("https://dave3600.cs.oslomet.no/~s375045/jsonin.php");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String postData = "navn=" + navn + "&beskrivelse=" + beskrivelse + "&gateadresse=" + adresse
                        + "&latitude=" + latLng.latitude + "&longitude=" + latLng.longitude;

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(postData);
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    handler.post(() -> {
                        // Opprett markør på kartet
                        String snippet = "Beskrivelse: " + beskrivelse + "\nAdresse: " + adresse +
                                "\nLatitude: " + latLng.latitude + "\nLongitude: " + latLng.longitude;
                        mMap.addMarker(new MarkerOptions().position(latLng).title(navn).snippet(snippet));
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
                // Hent data fra web-tjenesten
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
        if (mMap == null) {
            Toast.makeText(this, "Kartet er ikke klart ennå", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(response);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String navn = jsonObject.getString("navn");
                String beskrivelse = jsonObject.getString("beskrivelse");
                String adresse = jsonObject.getString("gateadresse");

                double latitude = Double.parseDouble(jsonObject.getString("latitude"));
                double longitude = Double.parseDouble(jsonObject.getString("longitude"));

                LatLng latLng = new LatLng(latitude, longitude);

                // Opprett infotekst for markør
                String snippet = "Beskrivelse: " + beskrivelse +
                        "\nAdresse: " + adresse +
                        "\nLatitude: " + latitude +
                        "\nLongitude: " + longitude;

                mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(navn)
                        .snippet(snippet));
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Feil i parsing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
