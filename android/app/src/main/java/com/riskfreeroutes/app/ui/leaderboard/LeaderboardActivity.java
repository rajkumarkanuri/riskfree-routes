package com.riskfreeroutes.app.ui.leaderboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityLeaderboardBinding;
import com.riskfreeroutes.app.databinding.ItemLeaderboardUserBinding;
import com.riskfreeroutes.app.model.User;
import com.riskfreeroutes.app.ui.reports.SubmitReportActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LeaderboardActivity — Displays community champions with intentional hierarchy:
 * Hero #1 spotlight, silver/bronze podium for ranks 2 & 3, and dense roster for ranks 4+.
 */
public class LeaderboardActivity extends AppCompatActivity {

    private static final String TAG = "LeaderboardAct";

    private ActivityLeaderboardBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private final List<User> allUsers = new ArrayList<>();
    private final List<User> rosterUsers = new ArrayList<>();
    private LeaderboardAdapter rosterAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLeaderboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        loadLeaderboard();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        binding.recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        rosterAdapter = new LeaderboardAdapter(rosterUsers);
        binding.recyclerLeaderboard.setAdapter(rosterAdapter);
    }

    private void setupClickListeners() {
        binding.btnEmptyReport.setOnClickListener(v -> {
            startActivity(new Intent(this, SubmitReportActivity.class));
        });

        binding.btnUserStandingReport.setOnClickListener(v -> {
            startActivity(new Intent(this, SubmitReportActivity.class));
        });
    }

    private void loadLeaderboard() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollContent.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.cardUserStanding.setVisibility(View.GONE);

        // Fetch users from Firestore and sort in-memory
        db.collection("users")
                .limit(100)
                .get()
                .addOnSuccessListener(snapshots -> {
                    binding.progressBar.setVisibility(View.GONE);
                    allUsers.clear();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            if (user.getUid() == null || user.getUid().isEmpty()) {
                                user.setUid(doc.getId());
                            }
                            allUsers.add(user);
                        }
                    }

                    if (allUsers.isEmpty()) {
                        showEmptyState();
                        return;
                    }

                    // Sort users by Trust Score descending, then verified reports descending
                    Collections.sort(allUsers, (u1, u2) -> {
                        int score1 = u1.getTrustScore() > 0 ? u1.getTrustScore() : (u1.getVerifiedReports() * 5 + u1.getReportsSubmitted() * 2);
                        int score2 = u2.getTrustScore() > 0 ? u2.getTrustScore() : (u2.getVerifiedReports() * 5 + u2.getReportsSubmitted() * 2);
                        return Integer.compare(score2, score1);
                    });

                    renderLeaderboard(allUsers);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Collection query restricted, falling back to current user doc: " + e.getMessage());
                    FirebaseUser currentUser = auth.getCurrentUser();
                    if (currentUser != null) {
                        db.collection("users").document(currentUser.getUid()).get()
                                .addOnSuccessListener(doc -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    if (doc.exists()) {
                                        User myUser = doc.toObject(User.class);
                                        if (myUser != null) {
                                            myUser.setUid(doc.getId());
                                            allUsers.clear();
                                            allUsers.add(myUser);
                                            renderLeaderboard(allUsers);
                                            return;
                                        }
                                    }
                                    showEmptyState();
                                })
                                .addOnFailureListener(err -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    showEmptyState();
                                });
                    } else {
                        binding.progressBar.setVisibility(View.GONE);
                        showEmptyState();
                    }
                });
    }

    private void renderLeaderboard(List<User> users) {
        binding.scrollContent.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);

        // ── 1. Hero #1 Champion ───────────────────────────────────────────────
        if (users.size() >= 1) {
            User hero = users.get(0);
            binding.cardHeroChampion.setVisibility(View.VISIBLE);
            
            String heroName = hero.getFullName() != null && !hero.getFullName().isEmpty() 
                    ? hero.getFullName() 
                    : (hero.getName() != null ? hero.getName() : "Top Guardian");
            binding.tvHeroName.setText(heroName);

            int heroScore = hero.getTrustScore() > 0 ? hero.getTrustScore() : Math.max(50, hero.getVerifiedReports() * 10);
            binding.tvHeroScore.setText(heroScore + " Trust Points");
            
            int heroReports = hero.getVerifiedReports() > 0 ? hero.getVerifiedReports() : hero.getReportsSubmitted();
            binding.tvHeroReportsCount.setText(heroReports + " verified community reports");

            if (hero.getProfileImageUrl() != null && !hero.getProfileImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(hero.getProfileImageUrl())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivHeroAvatar);
            } else {
                binding.ivHeroAvatar.setImageResource(R.drawable.ic_person);
            }
        } else {
            binding.cardHeroChampion.setVisibility(View.GONE);
        }

        // ── 2. Podium (Rank #2 & #3) ──────────────────────────────────────────
        if (users.size() >= 2) {
            binding.layoutPodium.setVisibility(View.VISIBLE);
            
            // Rank #2 (Silver)
            User rank2 = users.get(1);
            binding.cardRank2.setVisibility(View.VISIBLE);
            String name2 = rank2.getFullName() != null && !rank2.getFullName().isEmpty() ? rank2.getFullName() : rank2.getName();
            binding.tvRank2Name.setText(name2 != null ? name2 : "Guardian #2");
            int score2 = rank2.getTrustScore() > 0 ? rank2.getTrustScore() : Math.max(40, rank2.getVerifiedReports() * 10);
            binding.tvRank2Score.setText(score2 + " pts");
            int reports2 = rank2.getVerifiedReports() > 0 ? rank2.getVerifiedReports() : rank2.getReportsSubmitted();
            binding.tvRank2Reports.setText(reports2 + " verified");

            if (rank2.getProfileImageUrl() != null && !rank2.getProfileImageUrl().isEmpty()) {
                Glide.with(this).load(rank2.getProfileImageUrl()).circleCrop().placeholder(R.drawable.ic_person).into(binding.ivRank2Avatar);
            } else {
                binding.ivRank2Avatar.setImageResource(R.drawable.ic_person);
            }

            // Rank #3 (Bronze)
            if (users.size() >= 3) {
                User rank3 = users.get(2);
                binding.cardRank3.setVisibility(View.VISIBLE);
                String name3 = rank3.getFullName() != null && !rank3.getFullName().isEmpty() ? rank3.getFullName() : rank3.getName();
                binding.tvRank3Name.setText(name3 != null ? name3 : "Guardian #3");
                int score3 = rank3.getTrustScore() > 0 ? rank3.getTrustScore() : Math.max(30, rank3.getVerifiedReports() * 10);
                binding.tvRank3Score.setText(score3 + " pts");
                int reports3 = rank3.getVerifiedReports() > 0 ? rank3.getVerifiedReports() : rank3.getReportsSubmitted();
                binding.tvRank3Reports.setText(reports3 + " verified");

                if (rank3.getProfileImageUrl() != null && !rank3.getProfileImageUrl().isEmpty()) {
                    Glide.with(this).load(rank3.getProfileImageUrl()).circleCrop().placeholder(R.drawable.ic_person).into(binding.ivRank3Avatar);
                } else {
                    binding.ivRank3Avatar.setImageResource(R.drawable.ic_person);
                }
            } else {
                binding.cardRank3.setVisibility(View.INVISIBLE);
            }
        } else {
            binding.layoutPodium.setVisibility(View.GONE);
        }

        // ── 3. Ranks 4+ (Roster) ───────────────────────────────────────────────
        rosterUsers.clear();
        if (users.size() > 3) {
            for (int i = 3; i < users.size(); i++) {
                rosterUsers.add(users.get(i));
            }
            binding.headerRoster.setVisibility(View.VISIBLE);
            binding.tvRosterCount.setText(String.valueOf(rosterUsers.size()));
            binding.recyclerLeaderboard.setVisibility(View.VISIBLE);
            rosterAdapter.notifyDataSetChanged();
        } else {
            binding.headerRoster.setVisibility(View.GONE);
            binding.recyclerLeaderboard.setVisibility(View.GONE);
        }

        // ── 4. Personalized "Your Standing" Sticky Card ────────────────────────
        updateUserStandingCard(users);
    }

    private void updateUserStandingCard(List<User> users) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            binding.cardUserStanding.setVisibility(View.GONE);
            return;
        }

        String currentUid = currentUser.getUid();
        int userRank = -1;
        User myUserObj = null;

        for (int i = 0; i < users.size(); i++) {
            if (currentUid.equals(users.get(i).getUid())) {
                userRank = i + 1;
                myUserObj = users.get(i);
                break;
            }
        }

        binding.cardUserStanding.setVisibility(View.VISIBLE);

        if (userRank > 0 && myUserObj != null) {
            binding.tvUserStandingRank.setText("#" + userRank);
            int myScore = myUserObj.getTrustScore() > 0 ? myUserObj.getTrustScore() : 50;

            if (userRank == 1) {
                binding.tvUserStandingName.setText("You are leading the Leaderboard");
                binding.tvUserStandingHint.setText(myScore + " pts · Keep verifying routes to hold #1");
            } else if (userRank <= 3) {
                binding.tvUserStandingName.setText("You are on the Champions Podium");
                binding.tvUserStandingHint.setText(myScore + " pts · Ranked #" + userRank + " in your community");
            } else {
                binding.tvUserStandingName.setText("Your Community Standing");
                int reportsToPodium = Math.max(1, (userRank - 3) * 2);
                binding.tvUserStandingHint.setText(myScore + " pts · Submit ~" + reportsToPodium + " verified reports to reach Top 3");
            }
        } else {
            binding.tvUserStandingRank.setText("Unranked");
            binding.tvUserStandingName.setText("Start earning community trust");
            binding.tvUserStandingHint.setText("Submit road hazard reports to claim your rank");
        }
    }

    private void showEmptyState() {
        binding.scrollContent.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.cardUserStanding.setVisibility(View.GONE);
    }

    // ── Adapter for Ranks 4+ ──────────────────────────────────────────────────
    private class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.UserViewHolder> {

        private final List<User> list;

        public LeaderboardAdapter(List<User> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemLeaderboardUserBinding itemBinding = ItemLeaderboardUserBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new UserViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            User user = list.get(position);
            int rank = position + 4; // Roster starts at Rank 4

            holder.binding.tvRank.setText(String.valueOf(rank));
            
            String name = user.getFullName() != null && !user.getFullName().isEmpty()
                    ? user.getFullName()
                    : (user.getName() != null ? user.getName() : "Guardian");
            holder.binding.tvName.setText(name);

            int reports = user.getVerifiedReports() > 0 ? user.getVerifiedReports() : user.getReportsSubmitted();
            holder.binding.tvReportsCount.setText(reports + " verified reports");

            int score = user.getTrustScore() > 0 ? user.getTrustScore() : Math.max(20, reports * 5);
            holder.binding.tvScore.setText(score + " pts");

            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(user.getProfileImageUrl())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(holder.binding.ivAvatar);
            } else {
                holder.binding.ivAvatar.setImageResource(R.drawable.ic_person);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            final ItemLeaderboardUserBinding binding;
            UserViewHolder(ItemLeaderboardUserBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
