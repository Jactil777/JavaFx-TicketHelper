package com.jactil.javafx.tickethelper.component;

import com.jactil.javafx.tickethelper.util.StationUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 车站自动补全输入框
 * 继承 TextField，布局行为与普通输入框完全一致
 * 下拉列表使用 Popup 浮窗，不影响周围 UI 布局
 * 支持：输入时搜索车站 + 点击空输入框时显示历史记录
 */
public class StationAutoCompleteField extends TextField {

    private static final Logger logger = LoggerFactory.getLogger(StationAutoCompleteField.class);

    private final Popup popup;
    private final ListView<String> listView;
    private final ObservableList<String> suggestions = FXCollections.observableArrayList();
    private boolean suppressTextListener = false;

    /** 历史记录列表（由外部注入） */
    private List<String> historyItems;

    /** 当前是否正在显示历史记录（而非搜索结果） */
    private boolean showingHistory = false;

    /** 历史记录选中回调 */
    private javafx.util.Callback<String, Void> onHistorySelected;

    public StationAutoCompleteField(String promptText, double prefWidth) {
        super();
        setPromptText(promptText);
        setPrefWidth(prefWidth);
        getStyleClass().add("query-input");

        // 创建 Popup 浮窗
        popup = new Popup();
        popup.setAutoHide(true);

        listView = new ListView<>(suggestions);
        listView.setPrefWidth(prefWidth);
        listView.setMaxHeight(180);
        listView.getStyleClass().add("station-autocomplete-list");

        popup.getContent().add(listView);

        // 输入时触发搜索
        textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressTextListener) return;
            showingHistory = false;
            if (newVal == null || newVal.trim().isEmpty()) {
                hidePopup();
                return;
            }
            searchStations(newVal.trim());
        });

        // 点击输入框时，如果文本为空则显示历史记录
        setOnMouseClicked(e -> {
            if (getText() == null || getText().trim().isEmpty()) {
                showHistory();
            }
        });

        // 选中项后填入
        listView.setOnMouseClicked(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                setTextSilent(selected);
                hidePopup();
                requestFocus();

                // 如果是历史记录选中，通知外部保存
                if (showingHistory && onHistorySelected != null) {
                    onHistorySelected.call(selected);
                }
            }
        });

        // 失去焦点时隐藏
        focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                javafx.application.Platform.runLater(() -> {
                    if (!listView.isFocused()) {
                        hidePopup();
                    }
                });
            }
        });

        // 键盘导航
        setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DOWN:
                    if (popup.isShowing() && !suggestions.isEmpty()) {
                        int idx = listView.getSelectionModel().getSelectedIndex();
                        listView.getSelectionModel().select(Math.min(idx + 1, suggestions.size() - 1));
                        e.consume();
                    }
                    break;
                case UP:
                    if (popup.isShowing() && !suggestions.isEmpty()) {
                        int idx = listView.getSelectionModel().getSelectedIndex();
                        listView.getSelectionModel().select(Math.max(idx - 1, 0));
                        e.consume();
                    }
                    break;
                case ENTER:
                    if (popup.isShowing()) {
                        String selected = listView.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            setTextSilent(selected);
                            if (showingHistory && onHistorySelected != null) {
                                onHistorySelected.call(selected);
                            }
                        }
                        hidePopup();
                        e.consume();
                    }
                    break;
                case ESCAPE:
                    hidePopup();
                    e.consume();
                    break;
                default:
                    break;
            }
        });
    }

    /**
     * 设置历史记录列表（由 MainStage 在登录后注入）
     */
    public void setHistoryItems(List<String> historyItems) {
        this.historyItems = historyItems;
    }

    /**
     * 设置历史记录选中回调（用于保存选择到历史）
     */
    public void setOnHistorySelected(javafx.util.Callback<String, Void> callback) {
        this.onHistorySelected = callback;
    }

    private void searchStations(String keyword) {
        List<StationUtil.Station> results = StationUtil.search(keyword);
        // 转换为名称列表
        ObservableList<String> names = FXCollections.observableArrayList();
        for (StationUtil.Station s : results) {
            names.add(s.getName());
        }
        suggestions.setAll(names);
        if (names.isEmpty()) {
            hidePopup();
        } else {
            showPopup();
        }
    }

    /**
     * 显示历史记录列表
     */
    private void showHistory() {
        if (historyItems == null || historyItems.isEmpty()) {
            hidePopup();
            return;
        }
        showingHistory = true;
        suggestions.setAll(historyItems);
        showPopup();
    }

    private void showPopup() {
        if (!popup.isShowing()) {
            // 定位到输入框正下方
            popup.show(this,
                    localToScreen(0, 0).getX(),
                    localToScreen(0, 0).getY() + getHeight());
        }
        listView.getSelectionModel().selectFirst();
    }

    /** 程序化设置文本（不触发搜索），用于交换按钮等场景 */
    public void setTextSilent(String text) {
        suppressTextListener = true;
        super.setText(text);
        suppressTextListener = false;
    }

    private void hidePopup() {
        if (popup.isShowing()) {
            popup.hide();
        }
        listView.getSelectionModel().clearSelection();
    }
}
