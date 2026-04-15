package com.ifc.fitnessclub

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.*
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import java.text.NumberFormat

// --- UI THEME COLORS ---
val DarkBackground = Color(0xFF0F172A)
val CardBackground = Color(0xFF1E293B)
val PrimaryVibrant = Color(0xFF38BDF8)
val SecondaryVibrant = Color(0xFF818CF8)
val SuccessGreen = Color(0xFF4ADE80)
val WarningOrange = Color(0xFFFB923C)
val ErrorRed = Color(0xFFF87171)
val WhatsAppGreen = Color(0xFF25D366)

// Helper for Indian Currency Formatting
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(amount).replace("₹", "₹ ")
}

class MainActivity : ComponentActivity() {
    private val membersState = mutableStateListOf<Member>()
    private lateinit var auth: FirebaseAuth
    private var valueEventListener: ValueEventListener? = null
    private val databaseUrl = "https://ifc-fitness-default-rtdb.firebaseio.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        
        if (auth.currentUser != null) {
            fetchMembersFromFirebase()
        }

        setContent {
            var currentScreen by remember { mutableStateOf("splash") }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrimaryVibrant,
                    secondary = SecondaryVibrant,
                    background = DarkBackground,
                    surface = CardBackground,
                    error = ErrorRed
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "splash" -> GymSplashScreen {
                            currentScreen = if (auth.currentUser != null) "dashboard" else "login"
                        }
                        "login" -> LoginScreen(
                            onLoginSuccess = { 
                                fetchMembersFromFirebase()
                                currentScreen = "dashboard" 
                            },
                            onNavigateToRegister = { currentScreen = "register" }
                        )
                        "register" -> RegisterScreen(
                            onRegisterSuccess = { 
                                fetchMembersFromFirebase()
                                currentScreen = "dashboard" 
                            },
                            onNavigateToLogin = { currentScreen = "login" }
                        )
                        "dashboard" -> Box(modifier = Modifier.fillMaxSize()) {
                            GymDashboard(
                                members = membersState,
                                userEmail = auth.currentUser?.email ?: "Admin",
                                databaseUrl = databaseUrl,
                                onLogout = {
                                    logout()
                                    currentScreen = "login"
                                }
                            )

                            FloatingActionButton(
                                onClick = { currentScreen = "add_member" },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 24.dp, bottom = 100.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Member")
                            }
                        }
                        "add_member" -> GymAddMemberScreen(
                            databaseUrl = databaseUrl,
                            onBack = { currentScreen = "dashboard" }
                        )
                    }
                }
            }
        }
    }

    private fun fetchMembersFromFirebase() {
        val dbRef = FirebaseDatabase.getInstance(databaseUrl).getReference("Members")
        valueEventListener?.let { dbRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                membersState.clear()
                for (memberSnapshot in snapshot.children) {
                    memberSnapshot.getValue(Member::class.java)?.let { membersState.add(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) membersState.clear()
            }
        }
        valueEventListener = listener
        dbRef.addValueEventListener(listener)
    }

    private fun logout() {
        valueEventListener?.let {
            FirebaseDatabase.getInstance(databaseUrl).getReference("Members").removeEventListener(it)
        }
        valueEventListener = null
        auth.signOut()
        membersState.clear()
    }
}

@Composable
fun GymDashboard(members: List<Member>, userEmail: String, databaseUrl: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var memberFilter by remember { mutableStateOf("all") }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CardBackground) {
                val items = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("Members", Icons.AutoMirrored.Filled.List, 1),
                    Triple("Revenue", Icons.Default.Info, 2),
                    Triple("Profile", Icons.Default.Person, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { 
                            selectedTab = index 
                            if (index == 1) memberFilter = "all"
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryVibrant,
                            unselectedIconColor = Color.Gray,
                            selectedTextColor = PrimaryVibrant,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> MainContent(
                    members = members, 
                    onLogout = onLogout,
                    onNavigateToMembers = { filter ->
                        memberFilter = filter
                        selectedTab = 1
                    },
                    onNavigateToRevenue = {
                        selectedTab = 2
                    }
                )
                1 -> AllMembersContent(members, databaseUrl, memberFilter)
                2 -> MonthlyRevenueContent(members)
                3 -> ProfileContent(userEmail, onLogout)
            }
        }
    }
}

@Composable
fun MainContent(
    members: List<Member>, 
    onLogout: () -> Unit, 
    onNavigateToMembers: (String) -> Unit,
    onNavigateToRevenue: () -> Unit
) {
    val currentMonthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())
    val monthlyRevenue = members.filter { member ->
        try {
            val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(member.joinDate)
            date?.let { parsedDate -> SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(parsedDate) == currentMonthYear } ?: false
        } catch (_: Exception) { false }
    }.sumOf { it.amountPaid }

    Column(modifier = Modifier.padding(16.dp)) {
        DashboardHeader(onLogout)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clickable { onNavigateToRevenue() },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(PrimaryVibrant, SecondaryVibrant)))) {
                Column(modifier = Modifier.padding(20.dp).align(Alignment.CenterStart)) {
                    Text("This Month's Revenue", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(formatCurrency(monthlyRevenue), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                    Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, modifier = Modifier.size(80.dp).align(Alignment.CenterEnd).padding(end = 16.dp).alpha(0.2f), tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToMembers("all") }, 
                title = "Total Members", 
                value = "${members.size}", 
                icon = Icons.Default.Groups, 
                color = PrimaryVibrant
            )
            val expiring = members.count { getDaysLeft(it) in 0..5 }
            StatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToMembers("expiring_soon") }, 
                title = "Expiring Soon", 
                value = "$expiring", 
                icon = Icons.Default.NotificationImportant, 
                color = WarningOrange
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        val expired = members.count { getDaysLeft(it) < 0 }
        StatCard(
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToMembers("expired") }, 
            title = "Expired Membership", 
            value = "$expired", 
            icon = Icons.Default.History, 
            color = ErrorRed
        )
    }
}

@Composable
fun DashboardHeader(onLogout: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(id = R.drawable.img), contentDescription = "Logo", modifier = Modifier.size(45.dp).clip(CircleShape).border(1.dp, PrimaryVibrant, CircleShape), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "IFC Fitness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "Admin Panel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        IconButton(onClick = onLogout, modifier = Modifier.background(ErrorRed.copy(alpha = 0.1f), CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = ErrorRed, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AllMembersContent(members: List<Member>, databaseUrl: String, initialFilter: String = "all") {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Default") }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortOptions = listOf("Default", "Name (A-Z)", "Pending (High-Low)", "Joining Date (Newest)")

    val filteredMembers = members.filter { member ->
        val matchesSearch = member.name.contains(searchQuery, ignoreCase = true)
        val daysLeft = getDaysLeft(member)
        val matchesFilter = when (initialFilter) {
            "expired" -> daysLeft < 0
            "expiring_soon" -> daysLeft in 0..5
            else -> true
        }
        matchesSearch && matchesFilter
    }.let { list ->
        when (sortBy) {
            "Name (A-Z)" -> list.sortedBy { it.name }
            "Pending (High-Low)" -> list.sortedByDescending { it.pendingAmount }
            "Joining Date (Newest)" -> list.sortedByDescending { 
                try { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(it.joinDate) } catch (e: Exception) { Date(0) }
            }
            else -> list.sortedWith(compareByDescending<Member> { it.status == "Active" }.thenBy { getDaysLeft(it) })
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        val title = when(initialFilter) {
            "expired" -> "Expired Members"
            "expiring_soon" -> "Expiring Soon (5 days)"
            else -> "Members Directory"
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            Box {
                IconButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.Default.Sort, "Sort", tint = PrimaryVibrant)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sortBy = option; sortExpanded = false })
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f))
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (filteredMembers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing to show", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredMembers) { MemberItemWithDelete(it, databaseUrl) }
            }
        }
    }
}

@Composable
fun MemberItemWithDelete(member: Member, databaseUrl: String) {
    val daysLeft = getDaysLeft(member)
    val isExpired = daysLeft < 0
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Member") },
            text = { Text("Remove ${member.name} from records?") },
            confirmButton = { TextButton(onClick = { FirebaseDatabase.getInstance(databaseUrl).getReference("Members").child(member.id).removeValue(); showDeleteDialog = false }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text("Member Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Name", member.name)
                    DetailRow("Phone", member.phone)
                    DetailRow("Address", member.address)
                    DetailRow("Plan", member.plan)
                    DetailRow("Join Date", member.joinDate)
                    DetailRow("Paid", formatCurrency(member.amountPaid))
                    DetailRow("Pending", formatCurrency(member.pendingAmount))
                    val months = daysLeft / 30
                    val days = daysLeft % 30
                    val expStr = if (isExpired) "Expired" else if (months > 0) "$months months and $days days" else "$days days"
                    DetailRow("Status", if(isExpired) "Expired" else "Active ($expStr left)")
                }
            },
            confirmButton = { TextButton(onClick = { showDetailDialog = false }) { Text("Close") } }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if(isExpired) Color.Gray else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "Plan: ${member.plan}", fontSize = 11.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val monthsRemaining = daysLeft / 30
                    val daysRemaining = daysLeft % 30
                    val expirationText = if (isExpired) {
                        "Expired ${-daysLeft} days ago"
                    } else {
                        if (monthsRemaining > 0) "$monthsRemaining months $daysRemaining days" else "$daysLeft days"
                    }
                    Text(
                        text = expirationText,
                        fontSize = 10.sp,
                        color = when { daysLeft < 0 -> ErrorRed; daysLeft < 7 -> WarningOrange; else -> SuccessGreen },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (member.pendingAmount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(color = ErrorRed.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Text(text = "Pending: ${formatCurrency(member.pendingAmount)}", color = ErrorRed, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(if (isExpired) "Expired" else member.status, isExpired)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "$label:", color = Color.Gray, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

@Composable
fun StatusBadge(status: String, isExpired: Boolean) {
    val color = if (isExpired) ErrorRed else if (status == "Active") SuccessGreen else WarningOrange
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
        Text(text = status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun MonthlyRevenueContent(members: List<Member>) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    
    val monthlyData = (1..12).map { month ->
        val revenue = members.filter { member ->
            try {
                val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(member.joinDate)
                date?.let { calDate -> 
                    val c = Calendar.getInstance()
                    c.time = calDate
                    c.get(Calendar.MONTH) + 1 == month && c.get(Calendar.YEAR) == currentYear 
                } ?: false
            } catch (_: Exception) { false }
        }.sumOf { it.amountPaid }
        months[month-1] to revenue
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Financial Report", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PrimaryVibrant)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Lifetime Earnings", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(formatCurrency(members.sumOf { it.amountPaid }), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Monthly Breakdown ($currentYear)", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(monthlyData) { (m, r) ->
                Row(modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(m, fontWeight = FontWeight.Bold)
                    Text(formatCurrency(r), color = if(r > 0) SuccessGreen else Color.Gray, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun ProfileContent(email: String, onLogout: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAppInfoDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Data Collection: We collect gym member names, contact info, and payment details purely for club management.", fontSize = 13.sp)
                    Text("2. Usage: This data stays within your private Firebase database and is not shared with third parties.", fontSize = 13.sp)
                    Text("3. Security: All member records are secured via Firebase Authentication and strict Realtime Database rules.", fontSize = 13.sp)
                    Text("4. Your Rights: You can delete any member record at any time, which permanently removes their data.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Done") } }
        )
    }

    if (showAppInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAppInfoDialog = false },
            title = { Text("App Info & Features", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("IFC Fitness Club Manager v1.0", fontWeight = FontWeight.Bold, color = PrimaryVibrant)
                    Text("• Member Management: Add, track, and delete gym members.", fontSize = 13.sp)
                    Text("• Smart Expiry: Automatic countdown of membership days.", fontSize = 13.sp)
                    Text("• Financial Tracking: Real-time revenue reports and monthly breakdowns.", fontSize = 13.sp)
                    Text("• Cloud Sync: Secure Firebase database integration for instant updates.", fontSize = 13.sp)
                    Text("• Visual Overview: Dashboard for total, expiring, and expired members.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showAppInfoDialog = false }) { Text("Close") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Image(
                painter = painterResource(id = R.drawable.img), 
                null, 
                modifier = Modifier.size(140.dp).clip(CircleShape).border(3.dp, PrimaryVibrant, CircleShape),
                contentScale = ContentScale.Crop
            )
            Surface(color = SuccessGreen, shape = CircleShape, modifier = Modifier.size(24.dp).border(3.dp, DarkBackground, CircleShape)) {}
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("IFC Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(email, color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileMenuItem(icon = rememberVectorPainter(Icons.Default.Info), text = "App Info", color = Color.White) {
                    showAppInfoDialog = true
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))
                ProfileMenuItem(icon = painterResource(id = R.drawable.img_1), text = "Instagram", color = Color.Unspecified) {
                    uriHandler.openUri("https://www.instagram.com/ifc_fitnessclub?igsh=MWR4b2Q5MGFIZDAwNg==")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))
                ProfileMenuItem(icon = painterResource(id = R.drawable.img_2), text = "WhatsApp Group", color = Color.Unspecified) {
                    uriHandler.openUri("https://chat.whatsapp.com/L01n1WiTCzcFQV3wXGJ547?mode=gi_t")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.1f))
                ProfileMenuItem(icon = rememberVectorPainter(Icons.Default.Lock), text = "Privacy & Policy", color = Color.White) {
                    showPrivacyDialog = true
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileMenuItem(icon: Painter, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = icon, null, modifier = Modifier.size(24.dp).clip(CircleShape), colorFilter = if(color != Color.Unspecified) androidx.compose.ui.graphics.ColorFilter.tint(color) else null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun GymAddMemberScreen(databaseUrl: String, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var selectedPlanIndex by remember { mutableIntStateOf(0) }
    val plans = listOf("Monthly", "3 Months", "6 Months", "1 Year")
    var amountPaid by remember { mutableStateOf("") }
    var pendingAmount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardBackground, CircleShape)) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") 
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Add New Member", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.all { char -> char.isLetter() || char.isWhitespace() }) name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryVibrant) },
            shape = RoundedCornerShape(16.dp)
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) phone = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("+91 ") },
            leadingIcon = { Icon(Icons.Default.Phone, null, tint = PrimaryVibrant) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = PrimaryVibrant) },
            shape = RoundedCornerShape(16.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = plans[selectedPlanIndex],
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Plan") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = Color.White,
                    disabledBorderColor = Color.Gray.copy(alpha = 0.2f),
                    disabledLabelColor = Color.Gray,
                    disabledLeadingIconColor = PrimaryVibrant,
                    disabledTrailingIconColor = Color.Gray
                ),
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = PrimaryVibrant) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                shape = RoundedCornerShape(16.dp)
            )
            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
            
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                plans.forEachIndexed { index, plan ->
                    DropdownMenuItem(text = { Text(plan) }, onClick = { selectedPlanIndex = index; expanded = false })
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = amountPaid,
                onValueChange = { if (it.all { char -> char.isDigit() }) amountPaid = it },
                label = { Text("Paid (₹)") },
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Payments, null, tint = PrimaryVibrant) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = pendingAmount,
                onValueChange = { if (it.all { char -> char.isDigit() }) pendingAmount = it },
                label = { Text("Pending (₹)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("0", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                if (name.isNotEmpty() && phone.length == 10) {
                    val dbRef = FirebaseDatabase.getInstance(databaseUrl).getReference("Members")
                    val id = dbRef.push().key ?: ""
                    val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                    val m = Member(id, name, "+91 $phone", plans[selectedPlanIndex], address, amountPaid.toDoubleOrNull() ?: 0.0, pendingAmount.toDoubleOrNull() ?: 0.0, date, "Active")
                    dbRef.child(id).setValue(m).addOnSuccessListener { onBack() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = name.isNotEmpty() && phone.length == 10
        ) { Text("Confirm & Register", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun GymSplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(2000); onFinished() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.img), null, modifier = Modifier.size(180.dp).clip(RoundedCornerShape(40.dp)))
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = PrimaryVibrant, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
        }
    }
}

fun getDaysLeft(member: Member): Long {
    return try {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val joinDate = sdf.parse(member.joinDate) ?: return 9999L
        val planMonths = when(member.plan) {
            "Monthly" -> 1
            "3 Months" -> 3
            "6 Months" -> 6
            "1 Year" -> 12
            else -> member.plan.filter { it.isDigit() }.toIntOrNull() ?: 1
        }
        val cal = Calendar.getInstance().apply { time = joinDate; add(Calendar.MONTH, planMonths) }
        TimeUnit.DAYS.convert(cal.time.time - Date().time, TimeUnit.MILLISECONDS)
    } catch (_: Exception) { 9999L }
}
