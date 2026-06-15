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
 */
public class StationAutoCompleteField extends TextField {

    private static final Logger logger = LoggerFactory.getLogger(StationAutoCompleteField.class);

    private final Popup popup;
    private final ListView<StationUtil.Station> listView;
    private final ObservableList<StationUtil.Station> suggestions = FXCollections.observableArrayList();
    private boolean suppressTextListener = false;

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
            if (newVal == null || newVal.trim().isEmpty()) {
                hidePopup();
                return;
            }
            searchStations(newVal.trim());
        });

        // 选中车站后填入
        listView.setOnMouseClicked(e -> {
            StationUtil.Station selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                setTextSilent(selected.getName());
                hidePopup();
                requestFocus();
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
                        StationUtil.Station selected = listView.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            setText(selected.getName());
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

    private void searchStations(String keyword) {
        List<StationUtil.Station> results = StationUtil.search(keyword);
        suggestions.setAll(results);
        if (results.isEmpty()) {
            hidePopup();
        } else {
            showPopup();
        }
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
