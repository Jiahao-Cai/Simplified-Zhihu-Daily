package com.james.simplezhihudaily.View;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.james.simplezhihudaily.Util.RegexForZhihu;
import com.james.simplezhihudaily.Util.Symbol;
import com.james.simplezhihudaily.Util.Url;
import com.james.simplezhihudaily.R;
import com.james.simplezhihudaily.Util.Util;

import static com.james.simplezhihudaily.Util.Url.zhihuOfficial;
import static com.james.simplezhihudaily.View.MainActivity.mainActivity;

public class LoginActivity extends Activity implements View.OnClickListener {
    private EditText usernameText;
    private EditText passwordText;
    private EditText checksumBox;
    private Button login;
    private Button captchaImage;
    private RequestQueue mQueue;
    public LoginActivity loginActivity;
    private String XSRF;
    private String captchaString;
    private Gson gson;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor cookieEditor;
    private SharedPreferences.Editor accountEditor;
    private SharedPreferences account;
    private String username;
    private String password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_login);
        initWidget();
        getCheckSum();
    }

    private void initWidget() {
        usernameText = (EditText) findViewById(R.id.username);
        passwordText = (EditText) findViewById(R.id.password);
        checksumBox = (EditText) findViewById(R.id.captcha_input);
        captchaImage = (Button) findViewById(R.id.captcha);
        login = (Button) findViewById(R.id.login);
        login.setOnClickListener(this);
        captchaImage.setOnClickListener(this);
        loginActivity = this;
        sharedPreferences = getSharedPreferences("Cookie", MODE_PRIVATE);
        accountEditor = getSharedPreferences("Account", MODE_PRIVATE).edit();
        account = getSharedPreferences("Account", MODE_PRIVATE);
        cookieEditor = getSharedPreferences("Cookie", MODE_PRIVATE).edit();
        mQueue = Volley.newRequestQueue(loginActivity);
        username = account.getString("username", "");
        password = account.getString("password", "");
        usernameText.setText(username);
        passwordText.setText(password);
        gson = new Gson();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.login:
                captchaString = checksumBox.getText().toString();
                username = usernameText.getText().toString();
                password = passwordText.getText().toString();
                accountEditor.putString("username", username);
                accountEditor.putString("password", password);
                accountEditor.apply();
                fetchXSRF();
                break;
            case R.id.captcha:
                Log.d("Login", "changing captcha");
                getCheckSum();

        }
    }

    private void getCheckSum() {
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message message) {

            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                ImageRequest imageRequest = new ImageRequest(Url.getCheckSum, new Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(Bitmap response) {
                        BitmapDrawable bdrawable = new BitmapDrawable(loginActivity.getResources(), response);
                        captchaImage.setBackground(bdrawable);
                    }
                }, 0, 0, ImageView.ScaleType.FIT_XY, Bitmap.Config.RGB_565, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });
                mQueue.add(imageRequest);
                mQueue.start();
            }
        }).start();
    }

    private void fetchXSRF() {
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == Symbol.RECEIVE_SUCCESS)
                {
                    Log.d("Login", "Loading");
                    login();
                }
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                try
                {
                    Document document = Jsoup.connect(zhihuOfficial).get();
                    //Log.d("Login", document.toString());
                    Pattern getXSRF = Pattern.compile(RegexForZhihu.getXSRF);
                    Matcher matcher = getXSRF.matcher(document.toString());
                    //Log.d("Login",data);
                    XSRF = null;
                    if (matcher.find())
                    {
                        XSRF = matcher.group(2);
                    }
                    Log.d("Login", XSRF);
                    Message message = new Message();
                    message.what = Symbol.RECEIVE_SUCCESS;
                    handler.sendMessage(message);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void login() {
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == Symbol.RECEIVE_SUCCESS)
                {
                    Toast.makeText(loginActivity, "登录成功", Toast.LENGTH_SHORT).show();
                    //Intent intent = new Intent(loginActivity,MainActivity.class);
                    //startActivity(intent);
                    mainActivity.spinner.setSelection(0);
                    loginActivity.finish();
                } else
                {
                    Toast.makeText(loginActivity, "登录失败,请重试", Toast.LENGTH_SHORT).show();
                    passwordText.setText("");
                    checksumBox.setText("");
                }
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                try
                {
                    Connection.Response response = Jsoup.connect(Url.loginByMail).
                            data("_xsrf", XSRF,
                                    "captcha", captchaString,
                                    "email", username,
                                    "password", password,
                                    "remember_me", "true")
                            .header("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36")
                            .ignoreContentType(true)
                            .method(Connection.Method.POST).execute();
                    Log.d("StatusCode", String.valueOf(response.statusCode()));
                    if (response.statusCode() == 200)
                    {
                        Document document = response.parse();
                        Map<String, String> map = response.cookies();
                        Symbol.cookie = map;
                        cookieEditor.putString("cookie", map.toString());
                        cookieEditor.apply();
                        Log.d("Login_cookies_as_Map", map.toString());
                        Message message = new Message();
                        message.what = Symbol.RECEIVE_SUCCESS;
                        handler.sendMessage(message);
                        //Log.d("Login_cookies",response.cookies().toString());
                        //Log.d("Login_sessionId",sessionId);
                    } else
                    {
                        Message message = new Message();
                        message.what = Symbol.RECEIVER_FAILED;
                        handler.sendMessage(message);
                    }

                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        mainActivity.spinner.setSelection(Util.safeLongToInt(0));//将spinner变回
        this.finish();
    }


}