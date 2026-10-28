package com.example.medilinkchain;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {

    private List<EmergencyContact> contacts;

    public ContactsAdapter(List<EmergencyContact> contacts) {
        this.contacts = contacts;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        EmergencyContact contact = contacts.get(position);
        holder.name.setText(contact.getName());
        holder.relation.setText(contact.getRelation());
        holder.phone.setText(contact.getPhone());

        holder.callBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + contact.getPhone()));
            v.getContext().startActivity(intent);
        });

        holder.whatsappBtn.setOnClickListener(v -> {
            try {
                String message = "Emergency! I need help. My current location is being shared via MediLinkChain.";
                String url = "https://api.whatsapp.com/send?phone=" + contact.getPhone() + "&text=" + Uri.encode(message);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                v.getContext().startActivity(i);
            } catch (Exception e) {
                Toast.makeText(v.getContext(), "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView name, relation, phone;
        FloatingActionButton callBtn, whatsappBtn;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.contactName);
            relation = itemView.findViewById(R.id.contactRelation);
            phone = itemView.findViewById(R.id.contactPhone);
            callBtn = itemView.findViewById(R.id.callBtn);
            whatsappBtn = itemView.findViewById(R.id.whatsappBtn);
        }
    }
}
