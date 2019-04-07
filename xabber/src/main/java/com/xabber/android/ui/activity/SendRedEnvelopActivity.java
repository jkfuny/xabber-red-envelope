package com.xabber.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.xabber.android.R;

public class SendRedEnvelopActivity extends ManagedActivity {
    //金额
    EditText edtMoney;
    //祝福语
    EditText edtRemarks;
    //发红包
    Button btnSendRedEnvelope;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_red_envelope);
        edtMoney = findViewById(R.id.edt_money);
        edtRemarks = findViewById(R.id.edt_remarks);
        btnSendRedEnvelope = findViewById(R.id.btn_send_red_envelope);
        btnSendRedEnvelope.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btnSendRedEnvelope.isSelected()) {
                    String moneyStr = edtMoney.getText().toString();
                    String remarksStr = edtRemarks.getText().toString();
                    if (TextUtils.isEmpty(remarksStr)) {
                        remarksStr = edtRemarks.getHint().toString();
                    }
                    Intent intent = new Intent();
                    intent.putExtra("money", moneyStr);
                    intent.putExtra("remarks", remarksStr);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
        btnSelect();
        edtMoney.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                btnSelect();
            }
        });
    }

    private void btnSelect() {
        btnSendRedEnvelope.setSelected(!TextUtils.isEmpty(edtMoney.getText().toString()));
    }
}
