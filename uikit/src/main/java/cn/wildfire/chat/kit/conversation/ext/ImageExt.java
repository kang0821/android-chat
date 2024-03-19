/*
 * Copyright (c) 2020 WildFireChat. All rights reserved.
 */

package cn.wildfire.chat.kit.conversation.ext;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.lqr.imagepicker.ImagePicker;
import com.lqr.imagepicker.bean.ImageItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import cn.wildfire.chat.kit.R;
import cn.wildfire.chat.kit.annotation.ExtContextMenuItem;
import cn.wildfire.chat.kit.conversation.ext.core.ConversationExt;
import cn.wildfire.chat.kit.third.utils.ImageUtils;
import cn.wildfire.chat.kit.third.utils.UIUtils;
import cn.wildfirechat.message.TypingMessageContent;
import cn.wildfirechat.model.Conversation;
import cn.wildfirechat.remote.ChatManager;

public class ImageExt extends ConversationExt {

    /**
     * @param containerView 扩展view的container
     * @param conversation
     */
    @ExtContextMenuItem
    public void pickImage(View containerView, Conversation conversation) {
        String[] permissions = null;
        boolean isAccessFullOrPartialGranted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED)) {
            // Full access on Android 13 (API level 33) or higher
            isAccessFullOrPartialGranted = true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED) {
            // Partial access on Android 14 (API level 34) or higher
            isAccessFullOrPartialGranted = true;
        } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Full access up to Android 12 (API level 32)
            isAccessFullOrPartialGranted = true;
        } else {
            // Access denied
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                };
            } else {
                permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                };
            }
        }

        if (isAccessFullOrPartialGranted) {
            Intent intent = ImagePicker.picker().showCamera(true).enableMultiMode(9).buildPickIntent(activity);
            startActivityForResult(intent, 100);
            TypingMessageContent content = new TypingMessageContent(TypingMessageContent.TYPING_CAMERA);
            messageViewModel.sendMessage(conversation, content);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(permissions, 100);
            }
        }
    }

    private boolean isGifFile(String file) {
        try {
            FileInputStream inputStream = new FileInputStream(file);
            int[] flags = new int[5];
            flags[0] = inputStream.read();
            flags[1] = inputStream.read();
            flags[2] = inputStream.read();
            flags[3] = inputStream.read();
            inputStream.skip(inputStream.available() - 1);
            flags[4] = inputStream.read();
            inputStream.close();
            return flags[0] == 71 && flags[1] == 73 && flags[2] == 70 && flags[3] == 56 && flags[4] == 0x3B;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                ChatManager.Instance().getWorkHandler().post(() -> {
                    //是否发送原图
                    boolean compress = data.getBooleanExtra(ImagePicker.EXTRA_COMPRESS, true);
                    ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                    for (ImageItem imageItem : images) {
                        boolean isGif = isGifFile(imageItem.path);
                        if (isGif) {
                            UIUtils.postTaskSafely(() -> messageViewModel.sendStickerMsg(conversation, imageItem.path, null));
                            continue;
                        }
                        File imageFileThumb;
                        File imageFileSource = null;
                        // FIXME: 2018/11/29 压缩, 不是发原图的时候，大图需要进行压缩
                        if (compress) {
                            imageFileSource = ImageUtils.compressImage(imageItem.path);
                        }
                        imageFileSource = imageFileSource == null ? new File(imageItem.path) : imageFileSource;
//                    if (isOrig) {
//                    imageFileSource = new File(imageItem.path);
                        imageFileThumb = ImageUtils.genThumbImgFile(imageItem.path);
                        if (imageFileThumb == null) {
                            Log.e("ImageExt", "gen image thumb fail");
                            return;
                        }
//                    } else {
//                        //压缩图片
//                        // TODO  压缩的有问题
//                        imageFileSource = ImageUtils.genThumbImgFileEx(imageItem.path);
//                        //imageFileThumb = ImageUtils.genThumbImgFile(imageFileSource.getAbsolutePath());
//                        imageFileThumb = imageFileSource;
//                    }
//                            messageViewModel.sendImgMsg(conversation, imageFileThumb, imageFileSource);
                        File finalImageFileSource = imageFileSource;
                        UIUtils.postTaskSafely(() -> messageViewModel.sendImgMsg(conversation, imageFileThumb, finalImageFileSource));

                    }

                });

            }
        }
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public int iconResId() {
        return R.mipmap.ic_func_pic;
    }

    @Override
    public String title(Context context) {
        return "照片";
    }

    @Override
    public String contextMenuTitle(Context context, String tag) {
        return title(context);
    }
}
