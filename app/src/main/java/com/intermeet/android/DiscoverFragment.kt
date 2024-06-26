import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

import com.intermeet.android.CardStackAdapter
import com.intermeet.android.DiscoverViewModel
import com.intermeet.android.LikeAnimation
import com.intermeet.android.PassAnimation
import com.intermeet.android.R
import com.intermeet.android.UserDataModel
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.CardStackView
import com.yuyakaido.android.cardstackview.Direction


class DiscoverFragment : Fragment() {
    private val viewModel: DiscoverViewModel by viewModels()
    private lateinit var cardStackView: CardStackView
    private lateinit var adapter: CardStackAdapter
    private lateinit var noUsersTextView: TextView
    private lateinit var btnRefresh: TextView
    private lateinit var returnButton: View
    private lateinit var progressBar: ProgressBar
    private lateinit var manager: CardStackLayoutManager
    private var canRewind = true  // Initially allow rewinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("DiscoverFragment", "onViewCreated")

        cardStackView = view.findViewById(R.id.usersCardStackView)
        setupViews(view)
        setupListeners()
        setupCardStackView()
        addLikeAnimationFragment()
        addPassAnimationFragment()

        viewModel.filteredUsers.observe(viewLifecycleOwner) { users ->
            progressBar.visibility = View.GONE
            Log.d("DiscoverFragment", "Users: ${users.size}")
            if (users.isNotEmpty()) {
                updateAdapter(users)
            } else {
                displayNoUsers()
            }
        }

        fetchUsers(autoRefresh = false)
    }

    private fun setupViews(view: View) {
        cardStackView = view.findViewById(R.id.usersCardStackView)
        noUsersTextView = view.findViewById(R.id.tvNoUsers)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        returnButton = view.findViewById(R.id.return_button)
        progressBar = view.findViewById(R.id.loadingProgressBar)

        adapter = CardStackAdapter(requireContext(), mutableListOf())
        cardStackView.adapter = adapter
    }

    private fun setupListeners() {
        Log.d("DiscoverFragment", "setupListeners executed")
        returnButton.setOnClickListener {
            if (canRewind) {
                Log.d("DiscoverFragment", "Return button clicked")
                cardStackView.rewind()
            } else {
                Log.d("DiscoverFragment", "Cannot rewind after a right swipe")
            }
            updateReturnButtonDrawable()
        }

        btnRefresh.setOnClickListener {
            Log.d("DiscoverFragment", "Refresh button clicked")
            fetchUsers(autoRefresh = false)
            canRewind = false  // Reset rewind ability after refresh
            updateReturnButtonDrawable()
        }
    }

    private fun updateReturnButtonDrawable() {
        val drawableRes = if (canRewind) R.drawable.arrow_return_black else R.drawable.arrow_return
        returnButton.setBackgroundResource(drawableRes)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchUsers(autoRefresh: Boolean) {
        Log.d("DiscoverFragment", "fetchUsers executed with autoRefresh=$autoRefresh")
        progressBar.visibility = View.VISIBLE
        noUsersTextView.visibility = View.GONE
        btnRefresh.visibility = View.GONE
        viewModel.clearSeenUsers()
        viewModel.fetchAndFilterUsers()

        if (autoRefresh) {
            viewModel.filteredUserIdsLiveData.observe(viewLifecycleOwner) { userIds ->
                if (userIds.isEmpty()) {
                    displayNoUsers(autoRefresh = true)
                }
            }
        }
    }

    private fun displayNoUsers(autoRefresh: Boolean = false) {
        Log.d("DiscoverFragment", "displayNoUsers executed with autoRefresh=$autoRefresh")
        progressBar.visibility = View.GONE  // Ensure the progress bar is hidden
        cardStackView.visibility = View.GONE  // Hide the card stack view

        Handler().postDelayed({
            // Delayed changes in view after 2 seconds
            noUsersTextView.visibility = View.VISIBLE
            btnRefresh.visibility = View.VISIBLE

            if (autoRefresh) {
                fetchUsers(autoRefresh = true)
            }
        }, 1500) // 2 seconds
    }

    private fun updateAdapter(users: List<UserDataModel>) {
        Log.d("DiscoverFragment", "updateAdapter executed with user count: ${users.size}")
        adapter.setUsers(users)
        if (users.isEmpty()) {
            displayNoUsers()
        } else {
            cardStackView.visibility = View.VISIBLE
            noUsersTextView.visibility = View.GONE
            progressBar.visibility = View.GONE
        }
    }

    private fun setupCardStackView() {
        // Initialize the manager before attaching any listeners that might use it.
        manager = CardStackLayoutManager(context, object : CardStackListener {
            override fun onCardDragging(direction: Direction, ratio: Float) {
                // Handle card dragging
            }

            override fun onCardSwiped(direction: Direction) {
                val position = manager.topPosition - 1  // Index of the swiped card
                Log.d("DiscoverFragment", "Card swiped at position: $position")

                when (direction) {
                    Direction.Right -> {
                        val likedUserId = adapter.getUserIdAtPosition(position) ?: return
                        viewModel.addLike(likedUserId)
                        triggerLikeAnimation()
                        viewModel.markAsSeen(likedUserId)
                        canRewind = false  // Disable rewind after swiping right
                    }
                    Direction.Left -> {
                        triggerPassAnimation()
                        val passedUserId = adapter.getUserIdAtPosition(position)
                        viewModel.markAsSeen(passedUserId ?: "")
                        canRewind = true  // Enable rewind after swiping left
                    }
                    else -> {}
                }

                updateReturnButtonDrawable()

                // Check if adapter is empty and display "No Users" message
                if (manager.topPosition == adapter.itemCount) {
                    displayNoUsers()
                }
            }

            override fun onCardRewound() {
                // Handle card rewind
            }

            override fun onCardCanceled() {
                // Handle card cancel
            }

            override fun onCardAppeared(view: View, position: Int) {
                // Handle card appearance
                Handler(Looper.getMainLooper()).postDelayed({
                    view.animate().alpha(1.0f).setDuration(100).start()
                }, 500)
            }

            override fun onCardDisappeared(view: View, position: Int) {
                // Handle card disappearance
            }
        }).apply {
            // Set swipe directions and scrolling behavior
            setDirections(Direction.HORIZONTAL)
            setCanScrollVertical(false)
            setCanScrollHorizontal(true)
        }

        // Set the layout manager and adapter to the CardStackView
        cardStackView.layoutManager = manager
        cardStackView.adapter = adapter
    }

    private fun triggerLikeAnimation() {
        Log.d("DiscoverFragment", "triggerLikeAnimation executed")
        val likeAnimationFragment =
            childFragmentManager.findFragmentByTag("LikeAnimationFragment") as? LikeAnimation
        likeAnimationFragment?.let {
            it.animateLike()
            it.toggleBackgroundAnimation()
        }
    }

    private fun triggerPassAnimation() {
        Log.d("DiscoverFragment", "triggerPassAnimation executed")
        val passAnimationFragment =
            childFragmentManager.findFragmentByTag("PassAnimationFragment") as? PassAnimation
        passAnimationFragment?.let {
            it.animatePass()
            it.toggleBackgroundAnimation()
        }
    }

    private fun addLikeAnimationFragment() {
        val transaction = childFragmentManager.beginTransaction()
        val likeFragment = LikeAnimation()
        transaction.add(R.id.like_animation_container, likeFragment, "LikeAnimationFragment")
        transaction.commit()
        Log.d("DiscoverFragment", "Like animation fragment added")
    }

    private fun addPassAnimationFragment() {
        val transaction = childFragmentManager.beginTransaction()
        val passFragment = PassAnimation()
        transaction.add(R.id.like_animation_container, passFragment, "PassAnimationFragment")
        transaction.commit()
        Log.d("DiscoverFragment", "Pass animation fragment added")
    }

    companion object {
        fun newInstance() = DiscoverFragment()
    }
}