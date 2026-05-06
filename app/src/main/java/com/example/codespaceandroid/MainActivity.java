package com.example.codespaceandroid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final String[] MOCK_NEWS_TITLES = {
        "1. Show HN: Tiny tools for everyday file cleanup",
        "2. SQLite on mobile keeps getting better",
        "3. Building fast Android utilities with plain Java",
        "4. Why local-first apps feel instant",
        "5. A visual guide to media storage on Android",
        "6. Lessons from shipping small productivity apps",
        "7. The hidden costs of slow startup screens",
        "8. Designing simple batch workflows for phones",
        "9. How to make file operations predictable",
        "10. Notes on Android permissions in 2026",
        "11. A practical checklist for mobile backups",
        "12. Ask HN: What tools do you run on every phone?",
        "13. Faster lists without heavy frameworks",
        "14. Making mock data useful during development",
        "15. The case for boring app navigation",
        "16. Safer media renaming strategies",
        "17. Debug APKs and quick device testing",
        "18. Handling thousands of media rows locally",
        "19. When system pickers are not enough",
        "20. Small apps can still have sharp workflows"
    };

    private ArrayAdapter<String> newsAdapter;
    private final List<String> newsTitles = new ArrayList<>();
    private int lastClickedPosition = -1;
    private int consecutiveClickCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = new TextView(this);
        title.setText("Hacker News");
        title.setTextColor(Color.rgb(30, 30, 30));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(32, 28, 32, 20);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ListView newsList = new ListView(this);
        newsList.setDividerHeight(1);
        newsAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                newsTitles
        );
        newsList.setAdapter(newsAdapter);
        newsList.setOnItemClickListener((parent, view, position, id) -> handleNewsClick(position));
        root.addView(newsList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        newsTitles.addAll(Arrays.asList(MOCK_NEWS_TITLES));
        newsAdapter.notifyDataSetChanged();
    }

    private void handleNewsClick(int position) {
        if (position == lastClickedPosition) {
            consecutiveClickCount++;
        } else {
            lastClickedPosition = position;
            consecutiveClickCount = 1;
        }

        if (consecutiveClickCount >= 5) {
            consecutiveClickCount = 0;
            lastClickedPosition = -1;
            startActivity(new Intent(this, ToolsActivity.class));
        }
    }
}
