import React from 'react'
import PropTypes from 'prop-types'
import { makeStyles } from '@material-ui/core/styles'
import Grid from '@material-ui/core/Grid'
import Box from '@material-ui/core/Box'
import Skeleton from '@material-ui/lab/Skeleton'

// Placeholder shown while list data is loading, instead of a blank screen or a
// lone spinner. It adapts to the current theme automatically because MUI's
// Skeleton derives its shimmer color from theme.palette.action.hover.
const useStyles = makeStyles(
  (theme) => ({
    gridItem: {
      display: 'flex',
      flexDirection: 'column',
    },
    cover: {
      width: '100%',
      paddingTop: '100%', // square aspect ratio
      borderRadius: theme.shape.borderRadius,
    },
    row: {
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(2),
      padding: theme.spacing(1, 0),
    },
    rowAvatar: {
      flex: '0 0 auto',
    },
    rowText: {
      flex: '1 1 auto',
    },
  }),
  { name: 'NDSkeletonList' },
)

const AlbumGridSkeleton = ({ count, classes }) => (
  <Grid container spacing={2}>
    {Array.from({ length: count }).map((_, i) => (
      <Grid
        item
        xs={6}
        sm={4}
        md={3}
        lg={2}
        key={i}
        className={classes.gridItem}
      >
        <Skeleton variant="rect" className={classes.cover} />
        <Box mt={1}>
          <Skeleton variant="text" width="90%" />
          <Skeleton variant="text" width="60%" />
        </Box>
      </Grid>
    ))}
  </Grid>
)

const RowSkeleton = ({ count, classes, avatar }) => (
  <Box>
    {Array.from({ length: count }).map((_, i) => (
      <Box className={classes.row} key={i}>
        {avatar && (
          <Skeleton
            variant="rect"
            width={40}
            height={40}
            className={classes.rowAvatar}
          />
        )}
        <Box className={classes.rowText}>
          <Skeleton variant="text" width="45%" />
          <Skeleton variant="text" width="25%" />
        </Box>
      </Box>
    ))}
  </Box>
)

export const SkeletonList = ({ variant, count }) => {
  const classes = useStyles()
  switch (variant) {
    case 'album-grid':
      return <AlbumGridSkeleton count={count} classes={classes} />
    case 'artist-list':
      return <RowSkeleton count={count} classes={classes} avatar />
    case 'song-list':
    default:
      return <RowSkeleton count={count} classes={classes} avatar={false} />
  }
}

SkeletonList.propTypes = {
  variant: PropTypes.oneOf(['album-grid', 'song-list', 'artist-list']),
  count: PropTypes.number,
}

SkeletonList.defaultProps = {
  variant: 'song-list',
  count: 12,
}

export default SkeletonList
