package com.example.examplefour;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {
    private ContentValues values = new ContentValues();
    private TextView Button_camera;
    private TextView Button_camera2;
    private ImageView image_camera;
    private EditText bp_byte;
    private static byte[] bp_in_byte;
    static int bp_id;
    static byte[] bp_IV;
    static String bp_id_alias;
    private static Cursor cr;
    private static Cursor contacts;
    private static SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        image_camera = (ImageView) findViewById(R.id.image_camera);
        bp_byte = (EditText) findViewById(R.id.bp_byte);
        Button_camera = (TextView) findViewById(R.id.button_camera);
        Button_camera2 = (TextView) findViewById(R.id.button_camera2);
        loadbd(); // загрузка БД
        loadimage(); // деширфрование и вывод последней записи
        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        bp_byte.setFocusable(false); // анологично readonly=true


        Button_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera();
            }
        });
        Button_camera2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteCamera();
            }
        });
    }

    private void deleteCamera() { // удаление записи из БД
        int uri = getContentResolver().delete(exampleprovider.CONTACT_CONTENT_URI,
                "_id = ?",
                new String[]{String.valueOf(bp_id)});
        int bp_cur = bp_id;
        loadbd();
        loadimage();
        if (bp_cur == bp_id + 1) {
            SharedPreferences.Editor edit = sharedPref.edit();
            edit.putInt("max_count", bp_cur);
            edit.apply();
        }
    }

    private void openCamera() { // передача Intent функции камеры
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, 0);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) { // обработка данных, шифрование, добавление записей в БД
            loadbd();
            Bitmap bp = (Bitmap) data.getExtras().get("data");
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bp.compress(Bitmap.CompressFormat.PNG, 0, stream);
            bp_in_byte = stream.toByteArray();
            int max_id = sharedPref.getInt("max_count", bp_id);
            if (bp_id + 1 == max_id) {
                bp_id_alias = String.valueOf(max_id + 1);
            }
            else {
                bp_id_alias = String.valueOf(bp_id + 1);
            }
            EncryptionUtility encription = new EncryptionUtility(bp_in_byte, bp_id_alias);
            byte[] encrypt = encription.encrypt();
            bp_IV = encription.getIV();
            values.put("image", encrypt); // добавление зашифрованной записи в БД
            values.put("VI", bp_IV); // добавление ключа инициализации в БД
            Uri uri = getContentResolver().insert(exampleprovider.CONTACT_CONTENT_URI, values);
            loadbd(); // обновление курсора
            loadimage(); // вывод добавленной записи
        }
    }

    private void loadbd() { // метод загрузки данных в курсор
        cr = getContentResolver().query(exampleprovider.CONTACT_CONTENT_URI,
                null,
                null,
                null,
                "_id");
        cr.moveToLast();
        if (cr.getCount() != 0) {
            bp_id = cr.getInt(0);
            bp_byte.setText(String.valueOf(bp_id));
        }
    }

    private void loadimage() { // чтение курсора, вывод данных на loadimage
        if (cr.getCount() != 0) {
            bp_id = cr.getInt(0);
            bp_byte.setText(String.valueOf(bp_id));
            bp_in_byte = cr.getBlob(1);
            bp_IV = cr.getBlob(2);
            bp_id_alias = String.valueOf(bp_id);
            EncryptionUtility decryption = new EncryptionUtility(bp_in_byte, bp_id_alias);
            byte[] decrypt;
            try {
                decryption.IV = bp_IV;
                decrypt = decryption.decrypt();
                image_camera.setImageBitmap(BitmapFactory.decodeByteArray(decrypt,
                        0,
                        decryption.decrypt().length));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void backimage(View view) { // переход на прошлую запись
        try {
            cr.moveToPrevious();
            loadimage();
        } catch (Exception e) {
            Toast.makeText(this, "Конец каталога", Toast.LENGTH_LONG).show();
        }
    }

    public void nextimage(View view) { // переход на следующую запись
        try {
            cr.moveToNext();
            loadimage();
        } catch (Exception e) {
            Toast.makeText(this, "Конец каталога", Toast.LENGTH_LONG).show();
        }
    }

    private void readcontacts() { // чтение количества контактов на телефоне
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    0);
        }
        contacts = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                null,
                null,
                null,
                null);
        Toast.makeText(this, String.valueOf(contacts.getCount()), Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { // меню активности

        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { // обработка меню
        switch (item.getItemId()) {
            case R.id.show_contact:
                readcontacts();
                return true;
            case R.id.show_data:
                if (cr.getCount() != 0) {
                    String encrypted_to_show = new String(bp_in_byte);
                    Toast.makeText(this, encrypted_to_show, Toast.LENGTH_LONG).show();
                }
                return true;
            default:

        }
        return super.onOptionsItemSelected(item);
    }


}
