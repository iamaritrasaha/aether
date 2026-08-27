package com.foresightlabs.aether.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.ChatFolder
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

@Composable
fun FolderManagementSheet(
    isVisible: Boolean,
    folders: List<ChatFolder>,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onEditFolder: (Int, String) -> Unit,
    onDeleteFolder: (Int) -> Unit,
    onReorderFolders: (List<Int>) -> Unit = {}
) {
    val colors = LocalAetherColors.current
    var isCreating by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var editingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var editingFolderName by remember { mutableStateOf("") }

    val customFolders = remember(folders) { folders.filter { it.id != 0 } }

    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .clip(AetherEmber.Shapes.RisingSheet)
                        .background(colors.surface)
                        .border(1.dp, colors.border, AetherEmber.Shapes.RisingSheet)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* keep taps inside */ }
                        .padding(top = 16.dp)
                        .navigationBarsPadding()
                        .testTag("folder_management_sheet")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chat Folders",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isCreating) {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    label = { Text("Folder Name", fontFamily = ManropeFontFamily) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.border,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("new_folder_name_field")
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontFamily = ManropeFontFamily,
                                        color = colors.textSecondary,
                                        modifier = Modifier
                                            .clickable { isCreating = false }
                                            .padding(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(AetherEmber.Shapes.Pill)
                                            .background(colors.accent)
                                            .clickable(enabled = newFolderName.isNotBlank()) {
                                                onCreateFolder(newFolderName.trim())
                                                newFolderName = ""
                                                isCreating = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .testTag("submit_create_folder")
                                    ) {
                                        Text(
                                            text = "Create",
                                            fontFamily = ManropeFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.surface
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isCreating = true }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                                    .testTag("create_folder_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Folder",
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Create New Folder",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(customFolders, key = { it.id }) { folder ->
                                val index = customFolders.indexOf(folder)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated)
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .testTag("folder_item_${folder.id}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = folder.title,
                                            fontFamily = ManropeFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        // Move Up
                                        if (index > 0) {
                                            IconButton(
                                                onClick = {
                                                    val reordered = customFolders.toMutableList()
                                                    val item = reordered.removeAt(index)
                                                    reordered.add(index - 1, item)
                                                    onReorderFolders(reordered.map { it.id })
                                                },
                                                modifier = Modifier.size(28.dp).testTag("move_up_${folder.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Move Up",
                                                    tint = colors.textSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Move Down
                                        if (index < customFolders.size - 1) {
                                            IconButton(
                                                onClick = {
                                                    val reordered = customFolders.toMutableList()
                                                    val item = reordered.removeAt(index)
                                                    reordered.add(index + 1, item)
                                                    onReorderFolders(reordered.map { it.id })
                                                },
                                                modifier = Modifier.size(28.dp).testTag("move_down_${folder.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Move Down",
                                                    tint = colors.textSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Edit
                                        IconButton(
                                            onClick = {
                                                editingFolder = folder
                                                editingFolderName = folder.title
                                            },
                                            modifier = Modifier.size(28.dp).testTag("edit_folder_${folder.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Delete
                                        IconButton(
                                            onClick = { onDeleteFolder(folder.id) },
                                            modifier = Modifier.size(28.dp).testTag("delete_folder_${folder.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename / Edit Folder Dialog
    if (editingFolder != null) {
        val targetFolder = editingFolder!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { editingFolder = null },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* stay inside */ }
                    .padding(20.dp)
            ) {
                Text(
                    text = "Edit Folder",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editingFolderName,
                    onValueChange = { editingFolderName = it },
                    label = { Text("Folder Name", fontFamily = ManropeFontFamily) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_folder_name_field")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = ManropeFontFamily,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clickable { editingFolder = null }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.Pill)
                            .background(colors.accent)
                            .clickable(enabled = editingFolderName.isNotBlank()) {
                                onEditFolder(targetFolder.id, editingFolderName.trim())
                                editingFolder = null
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("save_edit_folder")
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.surface
                        )
                    }
                }
            }
        }
    }
}
