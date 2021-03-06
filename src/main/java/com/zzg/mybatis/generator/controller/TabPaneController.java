package com.zzg.mybatis.generator.controller;

import com.jcraft.jsch.Session;
import com.zzg.mybatis.generator.model.DatabaseConfig;
import com.zzg.mybatis.generator.util.DbUtil;
import com.zzg.mybatis.generator.view.AlertUtil;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Project: mybatis-generator-gui
 *
 * @author github.com/slankka on 2019/1/22.
 */
public class TabPaneController extends BaseFXController {
    private static Logger logger = LoggerFactory.getLogger(TabPaneController.class);

    @FXML
    private TabPane tabPane;

    @FXML
    private DbConnectionController tabControlAController;

    @FXML
    private OverSshController tabControlBController;

    private boolean isOverssh;

    private MainUIController mainUIController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tabPane.setPrefHeight(((AnchorPane) tabPane.getSelectionModel().getSelectedItem().getContent()).getPrefHeight());
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            isOverssh = observable.getValue().getText().equals("SSH");
            tabPane.prefHeightProperty().bind(((AnchorPane) tabPane.getSelectionModel().getSelectedItem().getContent()).prefHeightProperty());
            getDialogStage().close();
            getDialogStage().show();
        });
    }

    public void setMainUIController(MainUIController mainUIController) {
        this.mainUIController = mainUIController;
        this.tabControlAController.setMainUIController(mainUIController);
        this.tabControlBController.setMainUIController(mainUIController);
    }

    public void setConfig(DatabaseConfig selectedConfig) {
        tabControlAController.setConfig(selectedConfig);
        tabControlBController.setDbConnectionConfig(selectedConfig);
        if (StringUtils.isNoneBlank(
                selectedConfig.getSshHost(),
                selectedConfig.getSshPassword(),
                selectedConfig.getSshPort(),
                selectedConfig.getSshUser(),
                selectedConfig.getLport())) {
            logger.info("Found SSH based Config");
            tabPane.getSelectionModel().selectLast();
        }
    }

    private DatabaseConfig extractConfigForUI() {
        if (isOverssh) {
            return tabControlBController.extractConfigFromUi();
        } else {
            return tabControlAController.extractConfigForUI();
        }
    }

    @FXML
    void saveConnection() {
        if (isOverssh) {
            tabControlBController.saveConfig();
        } else {
            tabControlAController.saveConnection();
        }
    }


    @FXML
    void testConnection() {
        DatabaseConfig config = extractConfigForUI();
        if (config == null) {
            return;
        }
        if (StringUtils.isAnyEmpty(config.getName(),
                config.getHost(),
                config.getPort(),
                config.getUsername(),
                config.getEncoding(),
                config.getDbType(),
                config.getSchema())) {
            AlertUtil.showWarnAlert("??????????????????????????????");
            return;
        }
        Session sshSession = DbUtil.getSSHSession(config);
        if (isOverssh && sshSession != null) {
            PictureProcessStateController pictureProcessState = new PictureProcessStateController();
            pictureProcessState.setDialogStage(getDialogStage());
            pictureProcessState.startPlay();
            //????????????????????????????????????????????????????????????
            Task task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    DbUtil.engagePortForwarding(sshSession, config);
                    DbUtil.getConnection(config);
                    return null;
                }
            };
            task.setOnFailed(event -> {
                Throwable e = task.getException();
                logger.error("task Failed", e);
                if (e instanceof RuntimeException) {
                    if (e.getMessage().equals("Address already in use: JVM_Bind")) {
                        tabControlBController.setLPortLabelText(config.getLport() + "????????????????????????????????????");
                    }
                    //?????????????????????????????????????????????????????????
                    pictureProcessState.playFailState("????????????:" + e.getMessage(), true);
                    return;
                }

                if (e.getCause() instanceof EOFException) {
                    pictureProcessState.playFailState("????????????, ??????????????????????????????????????????????????????????????????????????????", true);
                    //??????????????????????????????????????????????????????????????????????????????
                    DbUtil.shutdownPortForwarding(sshSession);
                    return;
                }
                pictureProcessState.playFailState("????????????:" + e.getMessage(), true);
                //???????????????????????????????????????????????????????????????????????????????????????
                DbUtil.shutdownPortForwarding(sshSession);
            });
            task.setOnSucceeded(event -> {
                try {
                    pictureProcessState.playSuccessState("????????????", true);
                    DbUtil.shutdownPortForwarding(sshSession);
                    tabControlBController.recoverNotice();
                } catch (Exception e) {
                    logger.error("", e);
                }
            });
            new Thread(task).start();
        } else {
            try {
                DbUtil.getConnection(config);
                AlertUtil.showInfoAlert("????????????");
            } catch (RuntimeException e) {
                logger.error("", e);
                AlertUtil.showWarnAlert("????????????, " + e.getMessage());
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                AlertUtil.showWarnAlert("????????????");
            }
        }
    }

    @FXML
    void cancel() {
        getDialogStage().close();
    }
}
