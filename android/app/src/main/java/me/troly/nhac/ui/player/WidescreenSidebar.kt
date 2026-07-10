package me.troly.nhac.ui.player

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import me.troly.nhac.ui.components.swipeGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log
import me.troly.nhac.data.subsonic.Song
import me.troly.nhac.data.subsonic.isServerTranscodeSuffix
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.components.formatDuration

@Composable
fun WidescreenSidebar(
    onShowSignalPath: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = LocalPlayer.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val playerState by player.state.collectAsState()
    
    val meta = playerState.current
    val art = meta?.artworkUri
    
    // Playback Progress Bar Position Tracking
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            player.tick()
            delay(500)
        }
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val duration = playerState.durationMs.coerceAtLeast(1)
    val progress = if (scrubbing) scrubValue else playerState.positionMs.toFloat() / duration

    // Interactive Audio Quality Badges & Visualizer State
    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            delay(1500)
        }
    }
    
    val extras = meta?.extras
    val suffix = extras?.getString("suffix")
    val bitRate = extras?.getInt("bitRate") ?: 0
    val bitDepth = extras?.getInt("bitDepth") ?: 0
    val samplingRate = extras?.getInt("samplingRate") ?: 0
    
    val isDsdOrHighRes = remember(suffix, bitDepth) {
        suffix?.lowercase() in setOf("dsf", "dff", "dsd") || bitDepth >= 24
    }
    
    // HQPlayer filter state from global persistent player connection
    val activeFilter by player.activeFilter.collectAsState()
    val activeDither by player.activeDither.collectAsState()
    val activeModulator by player.activeModulator.collectAsState()
    
    // Dynamic intelligent Audio Signal Path computation via AudioDecisionEngine
    val audioReport = remember(activeDevice, suffix, bitDepth, samplingRate, bitRate, activeFilter, activeDither, activeModulator) {
        me.troly.nhac.playback.AudioDecisionEngine.determineAudioPath(
            activeDevice = activeDevice,
            sourceSuffix = suffix,
            sourceBitDepth = bitDepth,
            sourceSamplingRate = samplingRate,
            sourceBitRate = bitRate,
            selectedFilter = activeFilter,
            selectedDither = activeDither,
            selectedModulator = activeModulator
        )
    }

    val ledColor = audioReport.ledColor
    val isDspEnabled = audioReport.isUpsampled

    val (formatName, originalSpecs) = remember(suffix, bitDepth, samplingRate, bitRate) {
        formatOriginalSpecs(suffix, bitDepth, samplingRate, bitRate)
    }

    // Media3 Queue State tracking
    var queueItems by remember { mutableStateOf(emptyList<androidx.media3.common.MediaMetadata>()) }
    var currentIndex by remember { mutableStateOf(-1) }
    
    LaunchedEffect(playerState) {
        queueItems = player.getQueue()
        currentIndex = player.getCurrentIndex()
    }

    // Navigation Tab Selection State (0: Queue, 1: Signal Path, 2: DSP Control)
    var activeTab by rememberSaveable { mutableStateOf(0) }

    // Last.fm Recommendation Engine States
    val recManager = player.recManager
    val autoRadioEnabled by recManager.autoRadioEnabled.collectAsState()
    val recSongs = remember { mutableStateListOf<Song>() }
    var isRecLoading by remember { mutableStateOf(false) }

    LaunchedEffect(meta) {
        if (meta != null) {
            val title = meta.title?.toString() ?: ""
            val artist = meta.artist?.toString() ?: ""
            val extras = meta.extras
            val artistId = extras?.getString("artistId")
            if (title.isNotBlank()) {
                isRecLoading = true
                try {
                    val recommendations = recManager.recommendSimilar(
                        currentSongTitle = title,
                        currentArtistName = artist,
                        currentArtistId = artistId,
                        repo = repo
                    )
                    recSongs.clear()
                    recSongs.addAll(recommendations)
                } catch (e: Exception) {
                    Log.e("SidebarRec", "Failed to fetch sidebar recommendations", e)
                } finally {
                    isRecLoading = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0F0E11))
    ) {
        // --- SECTION 1: NOW PLAYING MINI HEADER (Optimized for space) ---
        if (meta != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .swipeGestures(
                        onSwipeLeft = { player.next() },
                        onSwipeRight = { player.previous() }
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Album Art
                    AsyncImage(
                        model = art,
                        contentDescription = meta.title?.toString(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1D1A15))
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    
                    // Metadata & Quality Badge Row
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meta.title?.toString() ?: "Không rõ tên bài",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = meta.artist?.toString() ?: "Không rõ ca sĩ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC4BBA6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Interactive Quality Capsule (Clicking switches to Signal Path Tab inline!)
                val qualityCapsuleBg by animateColorAsState(
                    targetValue = if (activeTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0x12FFFFFF),
                    animationSpec = tween(250)
                )
                val qualityCapsuleScale by animateFloatAsState(
                    targetValue = if (activeTab == 1) 1.02f else 1.0f,
                    animationSpec = tween(200)
                )
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .graphicsLayer(scaleX = qualityCapsuleScale, scaleY = qualityCapsuleScale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(qualityCapsuleBg)
                        .clickable { activeTab = 1 } // switch to Signal Path tab inline!
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ledColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isDspEnabled) "DSP • ${activeModulator}" else "${suffix?.uppercase() ?: "AUDIO"} • Direct Bit-Perfect",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Playback Control & Slider Row
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { scrubbing = true; scrubValue = it },
                    onValueChangeFinished = { player.seekTo((scrubValue * duration).toLong()); scrubbing = false },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0x15FFFFFF),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMs(playerState.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA79C86)
                    )
                    
                    // Cohesive Playback Control Capsule
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x0CFFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { player.previous() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        
                        val playButtonScale by animateFloatAsState(
                            targetValue = if (playerState.isPlaying) 1.1f else 1.0f,
                            animationSpec = tween(150)
                        )
                        IconButton(
                            onClick = { player.togglePlay() },
                            modifier = Modifier
                                .size(38.dp)
                                .graphicsLayer(scaleX = playButtonScale, scaleY = playButtonScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        IconButton(onClick = { player.next() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }

                    Text(
                        text = formatMs(playerState.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA79C86)
                    )
                }
            }
        } else {
            // Empty state header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa chọn bài hát",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC4BBA6)
                )
            }
        }

        // Horizontal Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF231F27))
        )

        // --- SECTION 2: ROON-STYLE MULTI-TAB CONTROLLER ---
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF141318),
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("HÀNG ĐỢI", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("ĐƯỜNG TRUYỀN", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("BỘ LỌC DSP", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Tune, null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Tab Contents container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF111014))
                .swipeGestures(
                    onSwipeLeft = { if (activeTab < 2) activeTab += 1 },
                    onSwipeRight = { if (activeTab > 0) activeTab -= 1 }
                )
        ) {
            when (activeTab) {
                0 -> {
                    // TAB 1: DYNAMIC UP NEXT QUEUE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (queueItems.isNotEmpty()) {
                            val listState = rememberLazyListState()
                            
                            // Auto scroll to active index
                            LaunchedEffect(currentIndex) {
                                if (currentIndex >= 0 && currentIndex < queueItems.size) {
                                    listState.animateScrollToItem(currentIndex)
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(queueItems) { index, item ->
                                    val isPlayingItem = index == currentIndex
                                    val bgAnimatedColor by animateColorAsState(
                                        targetValue = if (isPlayingItem) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val textAnimatedColor by animateColorAsState(
                                        targetValue = if (isPlayingItem) MaterialTheme.colorScheme.primary else Color.White,
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val subAnimatedColor by animateColorAsState(
                                        targetValue = if (isPlayingItem) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color(0xFFC4BBA6),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val itemScale by animateFloatAsState(
                                        targetValue = if (isPlayingItem) 1.01f else 1.0f,
                                        animationSpec = tween(durationMillis = 150)
                                    )
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer(scaleX = itemScale, scaleY = itemScale)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bgAnimatedColor)
                                            .clickable { player.skipToQueueItem(index) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = subAnimatedColor,
                                            modifier = Modifier.width(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title?.toString() ?: "Bài hát",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isPlayingItem) FontWeight.Bold else FontWeight.Normal,
                                                color = textAnimatedColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.artist?.toString() ?: "Ca sĩ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = subAnimatedColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { player.removeQueueItem(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Xóa khỏi hàng đợi",
                                                tint = if (isPlayingItem) MaterialTheme.colorScheme.primary else Color(0xFF8C2F2A),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(color = Color(0xFF231F27))
                                    Spacer(Modifier.height(12.dp))
                                    
                                    // Radio Autoplay Toggle Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1B1A1E))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Nhạc Radio (Autoplay)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                "Tự động thêm nhạc tương tự khi hết hàng đợi",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF90897A)
                                            )
                                        }
                                        Switch(
                                            checked = autoRadioEnabled,
                                            onCheckedChange = { recManager.setAutoRadioEnabled(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            )
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }

                                if (recSongs.isNotEmpty()) {
                                    item {
                                        Text(
                                            "BÀI HÁT TƯƠNG TỰ (LAST.FM & THƯ VIỆN GỐC)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    
                                    itemsIndexed(recSongs) { index, song ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { 
                                                    player.appendSong(song)
                                                    player.skipToQueueItem(player.getQueue().size - 1)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = repo.config.coverArtUrl(song.coverArt, 120),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF231F27)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    song.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    song.artist ?: "Unknown Artist",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFC4BBA6),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    player.appendSong(song)
                                                    Toast.makeText(context, "Đã thêm vào cuối hàng đợi", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Thêm vào hàng đợi",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else if (isRecLoading) {
                                    item {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Hàng đợi trống. Chọn album để bắt đầu phát.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFC4BBA6),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 2: INLINE ROON-STYLE SIGNAL PATH (DYNAMICALLY DETERMINED)
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                    ) {
                        // 1. Source
                        SignalPathStep(
                            title = "1. FILE NGUỒN (SOURCE FILE)",
                            value = formatName,
                            subValue = originalSpecs,
                            isFirst = true,
                            color = ledColor
                        )
                        
                        // 2. Transcode / Stream
                        val isTranscoded = remember(suffix) { isServerTranscodeSuffix(suffix) }
                        val streamingValue = if (isTranscoded) "Server Transcoded (FLAC 24-bit)" else "Direct Stream (Nguyên bản)"
                        val streamingSub = if (isTranscoded) {
                            "Dữ liệu gốc định dạng $suffix được máy chủ tự động chuyển mã không hao tổn sang FLAC 24-bit PCM giúp Android giải mã tối ưu."
                        } else {
                            "Tệp âm thanh được truyền phát trực tiếp ở chất lượng nguyên bản từ máy chủ, không qua bất kỳ khâu nén nào."
                        }
                        SignalPathStep(
                            title = "2. PHƯƠNG THỨC TRUYỀN PHÁT (STREAMING)",
                            value = streamingValue,
                            subValue = streamingSub,
                            color = ledColor
                        )
                        
                        // 3. DSP Resampling Engine
                        val dspEngineTitle = if (audioReport.isUpsampled) {
                            "HQPlayer-grade Audiophile DSP"
                        } else {
                            "ExoPlayer Audio Engine (Float 32-bit)"
                        }
                        SignalPathStep(
                            title = "3. BỘ XỬ LÝ SỐ (AUDIO ENGINE / DSP)",
                            value = dspEngineTitle,
                            subValue = audioReport.dspEngineStatus,
                            color = ledColor
                        )
                        
                        // 4. Output Device details
                        val deviceDetails = "${activeDevice.techLabel} • Định dạng thực tế: ${audioReport.actualOutputFormat}\n${activeDevice.description}"
                        SignalPathStep(
                            title = "4. THIẾT BỊ ĐẦU RA (OUTPUT DEVICE)",
                            value = activeDevice.name,
                            subValue = deviceDetails,
                            color = ledColor
                        )
                        
                        // 5. Overall Audio Forecast
                        SignalPathStep(
                            title = "5. ĐÁNH GIÁ CHẤT LƯỢNG (AUDIO FORECAST)",
                            value = audioReport.statusLabel,
                            subValue = audioReport.statusDesc,
                            isLast = true,
                            color = ledColor
                        )
                    }
                }
                
                2 -> {
                    // TAB 3: HQPLAYER-GRADE DSP FILTER CONTROL (PERSISTENT & INTELLIGENT)
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "BỘ LỌC NỘI SUY SỐ & ĐIỀU CHẾ (HQPLAYER DSP)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Smart Hardware Recommendation Card
                        if (audioReport.recommendation != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Gợi ý tối ưu âm học",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = audioReport.recommendation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFE5DECD),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 1. Resampler Filter Option
                        Column {
                            Text("1. Resampler Filter (Bộ lọc nội suy)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Chỉ định bộ lọc cắt tần số biên dốc của âm thanh", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                            Spacer(Modifier.height(8.dp))
                            
                            val filterOptions = listOf("Bypass", "poly-sinc-xtr-lp", "Closed-Form-M", "sinc-L")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                             ) {
                                filterOptions.forEach { option ->
                                    val isSelected = activeFilter == option
                                    val bgAnimatedColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0EFFFFFF),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val textAnimatedColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5DECD),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val optionScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.02f else 1.0f,
                                        animationSpec = tween(durationMillis = 150)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .graphicsLayer(scaleX = optionScale, scaleY = optionScale)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bgAnimatedColor)
                                            .clickable { player.setFilter(option) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = textAnimatedColor
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 2. Dither / Noise Shaper Option
                        Column {
                            Text("2. Dither & Noise Shaper (Bộ phân dither)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Chuyển dịch nhiễu lượng tử cơ học ra khỏi dải nghe nhạy cảm", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                            Spacer(Modifier.height(8.dp))
                            
                            val ditherOptions = listOf("None", "Gauss dither", "NS9 (Shaper)", "LNS15")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ditherOptions.forEach { option ->
                                    val isSelected = activeDither == option
                                    val bgAnimatedColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0EFFFFFF),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val textAnimatedColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5DECD),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val optionScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.02f else 1.0f,
                                        animationSpec = tween(durationMillis = 150)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .graphicsLayer(scaleX = optionScale, scaleY = optionScale)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bgAnimatedColor)
                                            .clickable { player.setDither(option) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option.split(" ")[0],
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = textAnimatedColor
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 3. Modulator (PCM vs SDM)
                        Column {
                            Text("3. Modulator / Upsampler (Bộ điều chế SDM)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Cấu hình dồn mẫu Sigma-Delta lên dòng siêu cao tần 1-bit", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                            Spacer(Modifier.height(8.dp))
                            
                            val modulatorOptions = listOf("PCM (Bit-Perfect)", "DSD64 (Sigma-Delta)", "DSD512 (HQ-grade)")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                modulatorOptions.forEach { option ->
                                    val isSelected = activeModulator == option
                                    val bgAnimatedColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0EFFFFFF),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val textAnimatedColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5DECD),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val indicatorColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E676),
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val optionScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.01f else 1.0f,
                                        animationSpec = tween(durationMillis = 150)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer(scaleX = optionScale, scaleY = optionScale)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bgAnimatedColor)
                                            .clickable { player.setModulator(option) }
                                            .padding(vertical = 10.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(indicatorColor)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = option,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = textAnimatedColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Dynamic Hardware Specs matching note
                        val hardwareNote = activeDevice.hardwareNote
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x08FFFFFF))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = hardwareNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB300),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalPathStep(
    title: String,
    value: String,
    subValue: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp).padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(56.dp)
                        .background(color.copy(alpha = 0.2f))
                )
            }
        }
        
        Spacer(modifier = Modifier.width(6.dp))
        
        Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC4BBA6),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

private fun formatOriginalSpecs(suffix: String?, bitDepth: Int, samplingRate: Int, bitRate: Int): Pair<String, String> {
    val fmt = suffix?.lowercase() ?: "audio"
    val isDsd = fmt in setOf("dsf", "dff", "dsd")
    
    val formatName = when (fmt) {
        "dsf", "dff", "dsd" -> "DSD (Direct Stream Digital)"
        "aif", "aiff" -> "AIFF (Audio Interchange Format)"
        "flac" -> "FLAC (Lossless Audio)"
        "mp3" -> "MP3 (MPEG Audio)"
        "m4a", "aac" -> "AAC / ALAC (MPEG-4 Audio)"
        "wav" -> "WAV (Waveform Audio)"
        else -> fmt.uppercase()
    }
    
    val resolution = when {
        isDsd -> {
            if (samplingRate >= 11289600) "DSD256 (1-bit / 11.2 MHz)"
            else if (samplingRate >= 5644800) "DSD128 (1-bit / 5.6 MHz)"
            else "DSD64 (1-bit / 2.82 MHz)"
        }
        bitDepth > 0 && samplingRate > 0 -> {
            "${bitDepth}-bit / ${samplingRate / 1000.0} kHz"
        }
        samplingRate > 0 -> {
            "${samplingRate / 1000.0} kHz"
        }
        else -> "Chuẩn chất lượng CD"
    }
    
    val brString = if (bitRate > 0) "${bitRate} kbps" else ""
    val category = if (fmt in setOf("dsf", "dff", "dsd", "flac", "aif", "aiff", "wav")) "Studio Quality" else "Standard Quality"
    
    val details = listOfNotNull(
        resolution,
        if (brString.isNotEmpty()) brString else null,
        category
    ).joinToString(" • ")
    
    return Pair(formatName, details)
}

private fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())
