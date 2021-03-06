package kr.hs.emirim.w2015.pickone.Activity

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kr.hs.emirim.w2015.pickone.DataClass.*
import kr.hs.emirim.w2015.pickone.R
import kr.hs.emirim.w2015.pickone.databinding.ActivityChatRoomBinding
import kr.hs.emirim.w2015.pickone.databinding.ChatroomHeaderBinding
import kr.hs.emirim.w2015.pickone.databinding.ItemMessageBinding
import org.w3c.dom.Comment
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates

class ChatRoomActivity : AppCompatActivity() {
    private val fireDatabase = FirebaseDatabase.getInstance().reference
    private lateinit var chatRoomUid : String
    private var uid: String? = null
    private var recyclerView: RecyclerView? = null
    private var chatinfo : ChatInfoDTO? = null
    private lateinit var binding : ActivityChatRoomBinding
    private lateinit var bindingTool : ChatroomHeaderBinding
    lateinit var toggle : ActionBarDrawerToggle
    var booksinfo : BookDTO? = null
    var line : Long = 0L
    var isNew = true
    val userskey = ArrayList<String?>()

    @SuppressLint("SimpleDateFormat")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        bindingTool = ChatroomHeaderBinding.inflate(layoutInflater)
        setContentView (binding.root)

        val time = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("MM???dd??? hh:mm")
        val curTime = dateFormat.format(Date(time)).toString()
        chatRoomUid = intent.getStringExtra("chatRoomUid").toString()
        uid = Firebase.auth.currentUser?.uid.toString()
        recyclerView = findViewById (R.id.messageActivity_recyclerview)
        setSupportActionBar(binding.toolbar)

        // ???????????? ????????????
        toggle = ActionBarDrawerToggle(this, binding.drawer,R.string.open,R.string.close)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // ?????????????????? ?????? ??????
        toggle.syncState() // ?????? ?????????

        // ????????? ?????? ???
        binding.messageActivityImageView.setOnClickListener {
            Log.d("?????? ??? dest", "????????? ?????????")
            val comment = ChatDTO(
                binding.messageActivityEditText.text.toString(),
                uid,
                curTime
            )
            fireDatabase.child("chatrooms").child(chatRoomUid.toString()).child("chats").push()
                .setValue(comment.to_map())
                binding.messageActivityEditText.text = null
                Log.d ("chatUidNotNull dest", "$comment")
        }
        checkChatRoom()
    }

    // ????????? ?????? ?????????
    private fun checkChatRoom() {
        fireDatabase.child("chatrooms").child(chatRoomUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {}
                override fun onDataChange(snapshot: DataSnapshot) {
                    chatinfo = snapshot.child("chatinfo").getValue<ChatInfoDTO>()
                    supportActionBar?.title = chatinfo?.roomname
                    binding.messageActivityImageView.isEnabled = true
                    recyclerView?.layoutManager = LinearLayoutManager(this@ChatRoomActivity)
                    recyclerView?.adapter = RecyclerViewAdapter()

                    booksinfo = snapshot.child("book").getValue<BookDTO>()
                    bindingTool.headerBookname.text = booksinfo?.name as String?
                    bindingTool.headerBookwriter.text = booksinfo?.writer
                    for(user in snapshot.child("users").children){
                        if(user.key.equals(uid)){
                            // ?????? ????????? ?????? ??????????????? ????????????
                            line = user.value as Long
                            isNew = false
                        }
                    }
                    if(isNew) {
                        // ?????? ????????? ?????????
                        line = snapshot.child("chats").childrenCount as Long
                        fireDatabase.child("chatrooms").child("user").child(uid.toString()).setValue(line)
                    }
                }
            })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(toggle.onOptionsItemSelected(item)){
            return true
        }
        when(item.itemId){
            R.id.menu_userlist -> {
                // ????????? ?????? ????????????
                var msg = ""
                for(i in 0..userskey.size){
                    msg = msg+"\n"+userskey.get(i) as String?
                }
                AlertDialog.Builder(this)
                    .setTitle("????????? ????????????")
                    .setMessage(msg)
                    .setPositiveButton("??????",null)
                    .show()
            }
            R.id.menu_code ->{
                // ???????????? : ???????????? ???????????????
                //?????????????????????(ClipboardManager)??? ??????????????? ClipData??? id?????? ????????? ???????????? ????????? ??? ???????????????????????? set????????? ??????.
                val clip : ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;
                val clipData = ClipData.newPlainText("Roomkey",chatRoomUid as String?)
                clip.setPrimaryClip(clipData)
                Toast.makeText(this,"????????? ????????? ?????????????????????",Toast.LENGTH_SHORT).show()
            }
            R.id.menu_checkout->{
                // ?????????
                AlertDialog.Builder(this)
                    .setTitle("????????? ????????????")
                    .setMessage("???????????? ?????? ?????????.")
                    .setPositiveButton("??????"){ dalog,i ->
                        fireDatabase.child("chatrooms").child("users").child(uid.toString()).setValue(false)
                        Toast.makeText(this,"?????????????????????.",Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("??????",null)
                    .show()
            }
        }

        return super.onOptionsItemSelected(item)
    }
    // ????????? ?????????????????????
    inner class RecyclerViewAdapter : RecyclerView.Adapter<RecyclerViewAdapter.MessageViewHolder>() {
    // ????????? ???????????? users??? uid??? ?????? ????????? - map?????????
    // for?????? ???????????? ??????????????? map?????? uid??? ?????? ????????????
    // ?????????????????? ???????????? ????????? uid??? ????????? ????????? ??? ????????? ????????? ????????????
        private val comments = ArrayList<ChatDTO>()
        private val usersinfo = mapOf<String?, String?>()
        init {
            fireDatabase.child("chatrooms").child("users")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {}
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (item in snapshot.children) {
                            fireDatabase.child("users").child(item.key.toString())
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onCancelled(error: DatabaseError) {}
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        usersinfo.plus(mapOf(item.key to snapshot.child("nickname").value as String? ))
                                        userskey.add(snapshot.child("nickname").value as String?)
                                    }
                                })
                        }
                        getMessageList()
                    }
                })
        }

        fun getMessageList() {
            fireDatabase.child("chatrooms").child(chatRoomUid.toString()).child("chats")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {}
                    override fun onDataChange(snapshot: DataSnapshot) {
                        comments.clear()
                        // ???????????? ????????????
                        var count = 0
                        for (data in snapshot.children) {   // ??? ????????? ???????????? ???????????? ?????? ????????????
                            count+=1    // ???????????? ????????? 
                            if(count < line){   // ??????????????? ?????????
                                continue
                            }
                            val item = data.getValue<ChatDTO>()
                            comments.add(item!!)
                            println(comments)
                        }
                        notifyDataSetChanged()

                        recyclerView?.scrollToPosition(comments.size - 1)
                    }
                })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            return MessageViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(this@ChatRoomActivity),parent,false))
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val binding = (holder as MessageViewHolder).binding
            binding.messageItemTextViewMessage.textSize = 20F
            binding.messageItemTextViewMessage.text = comments[position].message
            binding.messageItemTextViewTime.text = comments[position].timestamp
            if (comments[position].user.equals(uid)) { // ?????? ??????
                binding.messageItemTextViewMessage.setBackgroundResource(R.drawable.mychat)
                binding.messageItemTextviewName.visibility = View.INVISIBLE
                binding.messageItemLayoutDestination.visibility =View.INVISIBLE
                binding.messageItemLinearlayoutMain.gravity = Gravity.RIGHT
            } else { // ????????? ??????
                Glide.with(holder.itemView.context).load(R.drawable.profile)
                    .apply(RequestOptions().circleCrop())
                    .into(binding.messageItemImageviewProfile)
                binding.messageItemTextviewName.text = usersinfo[comments[position].user]
                binding.messageItemLayoutDestination.visibility = View.VISIBLE
                binding.messageItemTextviewName.visibility = View.VISIBLE
                binding.messageItemTextViewMessage.setBackgroundResource(R.drawable.yourchat)
                binding.messageItemLinearlayoutMain.gravity =Gravity.LEFT
            }
        }
        inner class MessageViewHolder(val binding : ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemCount(): Int {
            return comments.size
        }
        
    }


}


